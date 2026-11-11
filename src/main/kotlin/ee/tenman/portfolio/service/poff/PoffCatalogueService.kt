package ee.tenman.portfolio.service.poff

import ee.tenman.portfolio.domain.ImdbRating
import ee.tenman.portfolio.domain.PoffCatalogue
import ee.tenman.portfolio.domain.PoffCatalogueResponse
import ee.tenman.portfolio.domain.PoffFilm
import ee.tenman.portfolio.domain.PoffSnapshot
import ee.tenman.portfolio.repository.PoffImdbRepository
import ee.tenman.portfolio.repository.PoffSnapshotRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

private val STALE = Duration.ofHours(20)
private val RETRY = Duration.ofHours(1)

@Service
class PoffCatalogueService(
  private val poffSnapshotRepository: PoffSnapshotRepository,
  private val poffImdbRepository: PoffImdbRepository,
  private val clock: Clock,
) {
  @Transactional(readOnly = true)
  fun catalogue(): PoffCatalogueResponse? {
    val snapshot = poffSnapshotRepository.findTopByOrderByIdDesc() ?: return null
    val stored = snapshot.catalogue ?: return null
    val ratings =
      poffImdbRepository
        .findByImdbIdIsNotNull()
        .mapNotNull { row -> row.imdbId?.let { row.filmId to ImdbRating(it, row.rating, row.votes) } }
        .toMap()
    return PoffCatalogueResponse(snapshot.syncedAt, stored.films.map { it.copy(imdb = ratings[it.id], stills = it.stills.map(::stillKey)) })
  }

  @Transactional
  fun store(catalogue: PoffCatalogue) {
    val snapshot = poffSnapshotRepository.findTopByOrderByIdDesc()
    val previous =
      snapshot
      ?.catalogue
      ?.takeUnless(::ended)
      ?.films
      .orEmpty()
    check(catalogue.films.isNotEmpty() && catalogue.films.size * 10 >= previous.size * 9) {
      "Import found ${catalogue.films.size} films where the stored catalogue has ${previous.size}"
    }
    check(screenings(catalogue.films) * 10 >= screenings(previous) * 9) {
      "Import found ${screenings(catalogue.films)} screenings where the stored catalogue has ${screenings(previous)}"
    }
    val row = snapshot ?: PoffSnapshot(catalogue, clock.instant())
    row.catalogue = catalogue
    row.syncedAt = clock.instant()
    poffSnapshotRepository.save(row)
  }

  @Transactional(readOnly = true)
  fun due(attempted: Instant?): Boolean {
    val snapshot = poffSnapshotRepository.findTopByOrderByIdDesc()
    val stored = snapshot?.catalogue ?: return attempted == null || Duration.between(attempted, clock.instant()) > RETRY
    val last = listOfNotNull(snapshot.syncedAt, attempted).max()
    if (Duration.between(last, clock.instant()) > STALE) return true
    val days = days(stored)
    if (days.isEmpty()) return false
    return LocalDate.now(clock) in days.min()..days.max()
  }

  @Transactional(readOnly = true)
  fun settled(listing: List<PoffListing>): Boolean {
    val stored = poffSnapshotRepository.findTopByOrderByIdDesc()?.catalogue ?: return false
    return ended(stored) && stored.films.map { it.id }.containsAll(listing.map { it.id })
  }

  private fun ended(catalogue: PoffCatalogue): Boolean {
    val days = days(catalogue)
    return days.isNotEmpty() && days.max() < LocalDate.now(clock)
  }

  private fun days(catalogue: PoffCatalogue): List<LocalDate> =
    catalogue.films.flatMap { it.screenings }.map { LocalDate.ofInstant(it.start, clock.zone) }

  private fun screenings(films: List<PoffFilm>): Int = films.sumOf { it.screenings.size }
}
