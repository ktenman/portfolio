package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class AripaevScheduleTest {
  private val tallinn = ZoneId.of("Europe/Tallinn")

  private fun row(
    start: String,
    end: String,
    slug: String? = "kuum-tool",
    series: String = "Kuum tool",
    title: String? = null,
    description: String? = null,
  ): String {
    val fields =
      listOfNotNull(
        """"start":"$start+02:00"""",
        """"end":"$end+02:00"""",
        """"series":"$series"""",
        slug?.let { """"seriesSlug":"$it"""" },
        title?.let { """"title":"$it"""" },
        description?.let { """"description":"$it"""" },
      )
    return fields.joinToString(",", "{", "}")
  }

  private fun payload(vararg rows: String) =
    """30:[["$","h1",null,{"children":[{"schedule":[${rows.joinToString(",")}],"episodeMedia":{"a":"b"}}]}]]""" + "\n31:\"x\""

  private fun read(vararg rows: String) = aripaevAirings(payload(*rows), tallinn)

  @Test
  fun `should fail with a clear message when the payload holds no schedule`() {
    expect { aripaevAirings("""30:[["$","h1",null,{"children":"Saatekava"}]]""", tallinn) }
      .toThrow<IllegalStateException>()
      .messageToContain("Äripäev schedule payload of 45 characters holds no schedule array")
  }

  @Test
  fun `should fail with a clear message when the schedule lists no shows`() {
    expect { read(row("2026-11-02T00:00:00", "2026-11-02T06:45:00", slug = null, series = "Muusika")) }
      .toThrow<IllegalStateException>()
      .messageToContain("Äripäev schedule lists no shows")
  }

  @Test
  fun `should read the array out of the longer text with the show times`() {
    val airing = read(row("2026-11-02T11:00:00", "2026-11-02T12:00:00", title = "Tarmo Noop")).single()
    expect(airing.start to airing.end).toEqual(Instant.parse("2026-11-02T09:00:00Z") to Instant.parse("2026-11-02T10:00:00Z"))
  }

  @Test
  fun `should drop a Muusika row without a series slug`() {
    val airings =
      read(
        row("2026-11-02T10:00:00", "2026-11-02T11:00:00", slug = null, series = "Muusika"),
        row("2026-11-02T11:00:00", "2026-11-02T12:00:00", title = "Tarmo Noop"),
      )
    expect(airings.map { it.show.name }).toContainExactly("Kuum tool")
  }

  @Test
  fun `should ignore a description that is an RSC reference`() {
    val airing = read(row("2026-11-02T11:00:00", "2026-11-02T12:00:00", title = "Tarmo Noop", description = "\$6b")).single()
    expect(airing.show.description).toEqual(null)
  }

  @Test
  fun `should clean a non-breaking space before the repeat mark`() {
    val airing = read(row("2026-11-02T18:00:00", "2026-11-02T19:00:00", title = "Tarmo Noop ühendab A. Le Coqi  *")).single()
    expect(airing.title).toEqual("Tarmo Noop ühendab A. Le Coqi")
  }

  @Test
  fun `should repeat the earliest row of the slug and cleaned title`() {
    val airings =
      read(
        row("2026-11-02T11:00:00", "2026-11-02T12:00:00", title = "Tarmo Noop"),
        row("2026-11-02T18:00:00", "2026-11-02T19:00:00", title = "Tarmo Noop  *"),
        row("2026-11-07T09:00:00", "2026-11-07T10:00:00", title = "Tarmo Noop *"),
      )
    expect(airings.map { it.originalExternalId }).toContainExactly(null, airings[0].externalId, airings[0].externalId)
  }

  @Test
  fun `should keep one title apart on two slugs`() {
    val airings =
      read(
        row("2026-11-02T11:00:00", "2026-11-02T12:00:00", title = "Ülevaade"),
        row("2026-11-02T12:00:00", "2026-11-02T13:00:00", slug = "toostus", series = "Tööstus", title = "Ülevaade"),
      )
    expect(airings.map { it.originalExternalId }).toContainExactly(null, null)
  }

  @Test
  fun `should give an untitled row the series name`() {
    val airing = read(row("2026-11-02T10:00:00", "2026-11-02T11:00:00")).single()
    expect(airing.title).toEqual("Kuum tool")
  }

  @Test
  fun `should repeat the first row of the slug that day for an untitled row`() {
    val airings =
      read(
        row("2026-11-02T11:00:00", "2026-11-02T12:00:00", title = "Tarmo Noop"),
        row("2026-11-02T18:00:00", "2026-11-02T19:00:00"),
      )
    expect(airings.map { it.originalExternalId }).toContainExactly(null, airings[0].externalId)
  }

  @Test
  fun `should keep the first untitled row of a weekday as an original`() {
    val airings =
      read(
        row("2026-11-02T10:00:00", "2026-11-02T11:00:00"),
        row("2026-11-03T10:00:00", "2026-11-03T11:00:00"),
      )
    expect(airings.map { it.originalExternalId }).toContainExactly(null, null)
  }

  @Test
  fun `should repeat the last untitled original of the slug for an untitled weekend row`() {
    val airings =
      read(
        row("2026-11-05T10:00:00", "2026-11-05T11:00:00"),
        row("2026-11-06T10:00:00", "2026-11-06T11:00:00"),
        row("2026-11-07T14:00:00", "2026-11-07T15:00:00"),
      )
    expect(airings.last().originalExternalId).toEqual(airings[1].externalId)
  }

  @Test
  fun `should repeat the weekday original for a second untitled row of a weekend day`() {
    val airings =
      read(
        row("2026-11-06T10:00:00", "2026-11-06T11:00:00"),
        row("2026-11-07T14:00:00", "2026-11-07T15:00:00"),
        row("2026-11-07T20:00:00", "2026-11-07T21:00:00"),
      )
    expect(airings.last().originalExternalId).toEqual(airings[0].externalId)
  }

  @Test
  fun `should repeat the original of a titled repeat for an untitled row after it`() {
    val airings =
      read(
        row("2026-11-02T11:00:00", "2026-11-02T12:00:00", title = "Tarmo Noop"),
        row("2026-11-03T11:00:00", "2026-11-03T12:00:00", title = "Tarmo Noop *"),
        row("2026-11-03T18:00:00", "2026-11-03T19:00:00"),
      )
    expect(airings.last().originalExternalId).toEqual(airings[0].externalId)
  }

  @Test
  fun `should keep the programme id of a slug above every Kuku id and below two to the 53`() {
    val show = read(row("2026-11-02T11:00:00", "2026-11-02T12:00:00", title = "Tarmo Noop")).single().show
    expect(show.externalId).toEqual(493_309_517_502_216)
  }

  @Test
  fun `should hash an airing id from the slug and the start second`() {
    val airing = read(row("2026-11-02T11:00:00", "2026-11-02T12:00:00", title = "Tarmo Noop")).single()
    expect(airing.externalId).toEqual(446_930_688_314_598)
  }
}
