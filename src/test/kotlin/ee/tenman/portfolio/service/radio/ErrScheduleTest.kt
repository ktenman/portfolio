package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.err.ErrScheduleItem
import org.junit.jupiter.api.Test
import java.time.Instant

class ErrScheduleTest {
  private fun item(
    radiomanId: Long = 45L,
    seriesName: String = "R2 Top 15",
    programName: String = seriesName,
    heading: String = "R2 Top 15",
    start: Long = 1795777500L,
    end: Long = 1795781100L,
    replay: Boolean = false,
    original: Long = 0L,
  ) = ErrScheduleItem(radiomanId, seriesName, programName, heading, start, end, replay, original)

  @Test
  fun `should link a repeat to the airing it repeats`() {
    val repeat = item(start = 1795889100L, end = 1795892700L, replay = true, original = 1795777500L)
    val airings = errAirings(Station.RAADIO2, listOf(item(), repeat))
    expect(airings.last().originalExternalId).toEqual(airings.first().externalId)
  }

  @Test
  fun `should mark no repeat on a first airing`() {
    expect(errAirings(Station.RAADIO2, listOf(item())).single().originalExternalId).toEqual(null)
  }

  @Test
  fun `should give an airing the same id on every import`() {
    expect(errAirings(Station.RAADIO2, listOf(item())).single().externalId).toEqual(id("RAADIO2/45/1795777500"))
  }

  @Test
  fun `should keep the programme of a renamed series`() {
    val airings = errAirings(Station.RAADIO2, listOf(item(), item(seriesName = "R2 Top 15 ⭐", start = 1795889100L, end = 1795892700L)))
    expect(airings.map { it.show.externalId }.distinct()).toHaveSize(1)
  }

  @Test
  fun `should keep the end of an airing across midnight`() {
    expect(errAirings(Station.RAADIO2, listOf(item(start = 1795813200L, end = 1795820400L))).single().end)
      .toEqual(Instant.ofEpochSecond(1795820400L))
  }

  @Test
  fun `should name the programme after its series and the airing after its heading`() {
    val airing = errAirings(Station.RAADIO2, listOf(item(seriesName = "R2 Hommik!", heading = "Esimene tund"))).single()
    expect(Triple(airing.show.name, airing.show.slug, airing.title)).toEqual(Triple("R2 Hommik!", "r2-hommik", "Esimene tund"))
  }

  @Test
  fun `should prefix the ids of Vikerraadio with its station`() {
    expect(errAirings(Station.VIKERRAADIO, listOf(item(radiomanId = 1282, seriesName = "Päevakaja"))).single().show.externalId)
      .toEqual(id("VIKERRAADIO/1282"))
  }

  @Test
  fun `should keep apart two series that share one name`() {
    val items =
      listOf(
        item(radiomanId = 1661, seriesName = "Uudistesaated", programName = "Keskpäevased uudised"),
        item(radiomanId = 1663, seriesName = "Uudistesaated", programName = "Pärastlõunased uudised"),
      )
    expect(errAirings(Station.VIKERRAADIO, items).map { it.show.name to it.show.externalId })
      .toContainExactly("Keskpäevased uudised" to id("VIKERRAADIO/1661"), "Pärastlõunased uudised" to id("VIKERRAADIO/1663"))
  }
}
