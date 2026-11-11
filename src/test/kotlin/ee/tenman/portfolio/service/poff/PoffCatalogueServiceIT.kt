package ee.tenman.portfolio.service.poff

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.PoffCatalogue
import ee.tenman.portfolio.repository.PoffImdbRepository
import ee.tenman.portfolio.repository.PoffSnapshotRepository
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

@IntegrationTest
class PoffCatalogueServiceIT {
  @Resource
  private lateinit var poffSnapshotRepository: PoffSnapshotRepository

  @Resource
  private lateinit var poffImdbRepository: PoffImdbRepository

  @Resource
  private lateinit var jdbcTemplate: JdbcTemplate

  private fun service(at: String = "2026-11-10T08:00:00Z") =
    PoffCatalogueService(poffSnapshotRepository, poffImdbRepository, tallinnClock(at))

  private fun catalogue(vararg ids: Long) = PoffCatalogue(ids.map { poffFilm(it) })

  private fun unreadable() =
    jdbcTemplate.update("INSERT INTO poff_snapshot (catalogue, synced_at) VALUES (?, '2026-11-10T07:00:00Z')", """{"films":[{"id":1}]}""")

  private val festival =
    PoffCatalogue(
      listOf(
        poffFilm(1, screenings = listOf(poffScreening(1, "2026-11-06T17:00:00Z"))),
        poffFilm(2, screenings = listOf(poffScreening(2, "2026-11-22T17:00:00Z"))),
      ),
    )

  @Test
  fun `stores the first catalogue`() {
    service().store(catalogue(1, 2))
    expect(
      poffSnapshotRepository
      .findTopByOrderByIdDesc()
      ?.catalogue
      ?.films
      ?.map { it.id },
        ).toEqual(listOf(1L, 2L))
  }

  @Test
  fun `replaces the stored catalogue with a new one`() {
    service().store(catalogue(1, 2))
    service().store(catalogue(3, 4))
    expect(poffSnapshotRepository.findAll().map { row -> row.catalogue?.films?.map { it.id } }).toEqual(listOf(listOf(3L, 4L)))
  }

  @Test
  fun `replaces a stored catalogue that cannot be read`() {
    unreadable()
    service().store(catalogue(3, 4))
    expect(poffSnapshotRepository.findAll().map { row -> row.catalogue?.films?.map { it.id } }).toEqual(listOf(listOf(3L, 4L)))
  }

  @Test
  fun `accepts a smaller catalogue once the stored festival has ended`() {
    service("2026-11-20T08:00:00Z").store(festival)
    service("2026-11-25T08:00:00Z").store(catalogue(3))
    expect(
      poffSnapshotRepository
      .findTopByOrderByIdDesc()
      ?.catalogue
      ?.films
      ?.map { it.id },
        ).toEqual(listOf(3L))
  }

  @Test
  fun `is settled after the festival when the listing is empty`() {
    service("2026-11-20T08:00:00Z").store(festival)
    expect(service("2026-11-25T08:00:00Z").settled(emptyList())).toEqual(true)
  }

  @Test
  fun `is not settled while no film has a screening`() {
    service().store(PoffCatalogue(listOf(poffFilm(1, screenings = emptyList()))))
    expect(service("2026-11-25T08:00:00Z").settled(listOf(PoffListing(1, "film-1")))).toEqual(false)
  }

  @Test
  fun `is due when the stored catalogue cannot be read`() {
    unreadable()
    expect(service().due(null)).toEqual(true)
  }

  @Test
  fun `keeps the stored catalogue when the new one has fewer than nine in ten of its films`() {
    service().store(catalogue(*LongArray(10) { it.toLong() }))
    expect { service().store(catalogue(*LongArray(8) { it.toLong() })) }.toThrow<IllegalStateException>()
    expect(
      poffSnapshotRepository
      .findTopByOrderByIdDesc()
      ?.catalogue
      ?.films
      ?.size,
        ).toEqual(10)
  }

  @Test
  fun `keeps the stored catalogue when the new one is empty`() {
    service().store(catalogue(1))
    expect { service().store(catalogue()) }.toThrow<IllegalStateException>()
    expect(
      poffSnapshotRepository
      .findTopByOrderByIdDesc()
      ?.catalogue
      ?.films
      ?.size,
        ).toEqual(1)
  }

  @Test
  fun `keeps the stored catalogue when the new one has fewer than nine in ten of its screenings`() {
    val bare = PoffCatalogue(listOf(1L, 2L).map { poffFilm(it, screenings = emptyList()) })
    service().store(catalogue(1, 2))
    expect { service().store(bare) }.toThrow<IllegalStateException>()
    expect(
      poffSnapshotRepository
      .findTopByOrderByIdDesc()
      ?.catalogue
      ?.films
      ?.sumOf { it.screenings.size },
        ).toEqual(2)
  }

  @Test
  fun `is due when nothing is stored`() {
    expect(service().due(null)).toEqual(true)
  }

  @Test
  fun `is due on a festival day`() {
    service("2026-11-10T08:00:00Z").store(festival)
    expect(service("2026-11-10T08:30:00Z").due(null)).toEqual(true)
  }

  @Test
  fun `is not due once the last festival day has ended in Tallinn`() {
    service("2026-11-22T22:30:00Z").store(festival)
    expect(service("2026-11-22T22:45:00Z").due(null)).toEqual(false)
  }

  @Test
  fun `is not due after the festival within twenty hours of the last sync`() {
    service("2026-11-23T08:00:00Z").store(festival)
    expect(service("2026-11-24T03:00:00Z").due(null)).toEqual(false)
  }

  @Test
  fun `is not due after the festival within twenty hours of a failed import`() {
    service("2026-11-23T08:00:00Z").store(festival)
    expect(service("2026-11-24T06:00:00Z").due(Instant.parse("2026-11-24T05:00:00Z"))).toEqual(false)
  }

  @Test
  fun `is due after the festival once the last sync is twenty hours old`() {
    service("2026-11-23T08:00:00Z").store(festival)
    expect(service("2026-11-24T04:01:00Z").due(null)).toEqual(true)
  }
}
