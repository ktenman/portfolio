package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.err.ErrScheduleItem
import org.junit.jupiter.api.Test
import java.time.Instant

class Raadio2ScheduleTest {
  private fun item(
    radiomanId: Long = 45L,
    seriesName: String = "R2 Top 15",
    heading: String = "R2 Top 15",
    start: Long = 1795777500L,
    end: Long = 1795781100L,
    replay: Boolean = false,
    original: Long = 0L,
  ) = ErrScheduleItem(radiomanId, seriesName, heading, start, end, replay, original)

  @Test
  fun `should link a repeat to the airing it repeats`() {
    val airings = raadio2Airings(listOf(item(), item(start = 1795889100L, end = 1795892700L, replay = true, original = 1795777500L)))
    expect(airings.last().originalExternalId).toEqual(airings.first().externalId)
  }

  @Test
  fun `should mark no repeat on a first airing`() {
    expect(raadio2Airings(listOf(item())).single().originalExternalId).toEqual(null)
  }

  @Test
  fun `should give an airing the same id on every import`() {
    expect(raadio2Airings(listOf(item())).single().externalId).toEqual(id("RAADIO2/45/1795777500"))
  }

  @Test
  fun `should keep the programme of a renamed series`() {
    val airings = raadio2Airings(listOf(item(), item(seriesName = "R2 Top 15 ⭐", start = 1795889100L, end = 1795892700L)))
    expect(airings.map { it.show.externalId }.distinct()).toHaveSize(1)
  }

  @Test
  fun `should keep the end of an airing across midnight`() {
    expect(raadio2Airings(listOf(item(start = 1795813200L, end = 1795820400L))).single().end).toEqual(Instant.ofEpochSecond(1795820400L))
  }

  @Test
  fun `should name the programme after its series and the airing after its heading`() {
    val airing = raadio2Airings(listOf(item(seriesName = "R2 Hommik!", heading = "Esimene tund"))).single()
    expect(Triple(airing.show.name, airing.show.slug, airing.title)).toEqual(Triple("R2 Hommik!", "r2-hommik", "Esimene tund"))
  }
}
