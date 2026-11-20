package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Station
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class RadioDigestTest {
  @Test
  fun `should name the digest after the week before it was built`() {
    expect(digestTitle(DigestShow.WEEK, LocalDate.of(2026, 11, 16))).toEqual("Week in Radio 09.11–15.11.2026")
  }

  @Test
  fun `should name the day show after the day it is built`() {
    expect(digestTitle(DigestShow.DAY, LocalDate.of(2026, 11, 17))).toEqual("Day in Radio 17.11.2026")
  }

  @Test
  fun `should take a row per topic with the minutes left after cuts`() {
    val topics = listOf(EpisodeChapter(100.0, "Ust-Luga"), EpisodeChapter(500.0, "Telia"))
    val held = cutRecording(RECORDING, topics, listOf(KeptSpan(0.0, 200.0), KeptSpan(300.0, 900.0)))
    expect(rows(held, TALLINN).map { it.title to it.kept }).toEqual(listOf("Ust-Luga" to 300.0, "Telia" to 400.0))
  }

  @Test
  fun `should drop a topic with under a minute kept`() {
    val held =
      cutRecording(RECORDING, listOf(EpisodeChapter(100.0, "Saate algus"), EpisodeChapter(150.0, "Sõda")), listOf(KeptSpan(0.0, 900.0)))
    expect(rows(held, TALLINN).map { it.title }).toEqual(listOf("Sõda"))
  }

  @Test
  fun `should name the programme of the boundary a topic starts in`() {
    val boundaries =
      listOf(
        Boundary("show starts", 0.0, "transcript", false, programme = "Uudised"),
        Boundary("show starts", 400.0, "transcript", false, programme = "Kuum tool"),
      )
    val held = cutRecording(RECORDING, listOf(EpisodeChapter(100.0, "Ilm"), EpisodeChapter(500.0, "Tööturg")), boundaries = boundaries)
    expect(rows(held, TALLINN).map { it.programme }).toEqual(listOf("Uudised", "Kuum tool"))
  }

  @Test
  fun `should not count a recording whose cuts are still being found as finished`() {
    expect(finished(cutRecording(RECORDING, emptyList(), state = ProgrammeCutState.QUEUED))).toEqual(false)
  }

  @Test
  fun `should score importance twice and cap other shows at two`() {
    val rows = table("Digitund", "Samost", "Kuum tool", "Tervisetark")
    val found = ranked(Judgement(listOf(story("r1", listOf("r1", "r2", "r3", "r4"), 5, 3))), rows)
    expect(found.map { it.score }).toEqual(listOf(15))
  }

  @Test
  fun `should drop stories scoring under eight`() {
    expect(ranked(Judgement(listOf(story("r1", listOf("r1"), 3, 1))), table("Digitund"))).toEqual(emptyList())
  }

  @Test
  fun `should keep at most four stories from one show`() {
    val stories = (1..6).map { story("r$it", listOf("r$it"), 4, it % 5 + 1) }
    val found = ranked(Judgement(stories), table(*Array(6) { "Samost ja Aaspõllu" }))
    expect(found.map { it.story.interest }).toEqual(listOf(5, 4, 3, 2))
  }

  @Test
  fun `should drop a story the judge marks as a repeat`() {
    expect(ranked(Judgement(listOf(story("r1", listOf("r1"), 5, 5, repeat = true))), table("Digitund"))).toEqual(emptyList())
  }

  @Test
  fun `should drop unknown rows and a story whose best is not among its rows`() {
    val stories = listOf(story("r1", listOf("r1", "r9"), 5, 5), story("r2", listOf("r1"), 5, 5))
    expect(ranked(Judgement(stories), table("Digitund", "Kuum tool")).map { it.best.id to it.score }).toEqual(listOf("r1" to 15))
  }

  @Test
  fun `should not play the same take for two stories`() {
    val stories = listOf(story("r1", listOf("r1"), 5, 5), story("r1", listOf("r1", "r2"), 4, 4))
    expect(ranked(Judgement(stories), table("Digitund", "Kuum tool")).map { it.score }).toEqual(listOf(15))
  }

  @Test
  fun `should take the lines the model picks as the clip`() {
    val lines = (0..9).map { line(1000.0 + it * 30, 1000.0 + it * 30 + 28) }
    expect(clipped(ClipAnswer(2, 5), lines, row(1000.0, 1400.0), FULL)).toEqual(1030.0 to 1148.0)
  }

  @Test
  fun `should fall back to the first three minutes when the answer is outside the bounds`() {
    val lines = (0..19).map { line(1000.0 + it * 30, 1000.0 + it * 30 + 28) }
    val answers = listOf(null, ClipAnswer(2, 2), ClipAnswer(1, 12), ClipAnswer(3, 21), ClipAnswer(5, 3))
    expect(answers.map { clipped(it, lines, row(1000.0, 1600.0), FULL) }).toEqual(List(5) { 1000.0 to 1180.0 })
  }

  @Test
  fun `should keep a clip that spans a removed ad break contiguous`() {
    val lines = listOf(line(100.0, 140.0), line(200.0, 230.0), line(240.0, 300.0))
    val audible = listOf(KeptSpan(0.0, 150.0), KeptSpan(200.0, 400.0))
    expect(clipped(ClipAnswer(1, 3), lines, row(100.0, 400.0), audible)).toEqual(100.0 to 250.0)
  }

  @Test
  fun `should skip a clip that would take the digest past an hour`() {
    val ranked = RankedStory(story("r1", listOf("r1"), 5, 5), row(0.0, 600.0), emptyList(), 15)
    val clips = listOf(1500.0, 1500.0, 900.0, 500.0).map { DigestClip(ranked, cutRecording(RECORDING, emptyList()), 0.0, it, emptyList()) }
    expect(fitted(clips, DigestShow.WEEK.seconds).map { it.length }).toEqual(listOf(1500.0, 1500.0, 500.0))
  }

  @Test
  fun `should skip a clip that would take the day show past half an hour`() {
    val ranked = RankedStory(story("r1", listOf("r1"), 5, 5), row(0.0, 600.0), emptyList(), 15)
    val clips = listOf(900.0, 900.0, 600.0, 400.0).map { DigestClip(ranked, cutRecording(RECORDING, emptyList()), 0.0, it, emptyList()) }
    expect(fitted(clips, DigestShow.DAY.seconds).map { it.length }).toEqual(listOf(900.0, 600.0))
  }

  @Test
  fun `should count the pause after each clip toward the hour`() {
    val ranked = RankedStory(story("r1", listOf("r1"), 5, 5), row(0.0, 600.0), emptyList(), 15)
    val clips = listOf(1800.0, 1800.0).map { DigestClip(ranked, cutRecording(RECORDING, emptyList()), 0.0, it, emptyList()) }
    expect(fitted(clips, DigestShow.WEEK.seconds).map { it.length }).toEqual(listOf(1800.0))
  }

  @Test
  fun `should give the digest the station heard longest`() {
    val stories = listOf(told(Station.KUKU, 0.0, 60.0), told(Station.ARIPAEV, 61.0, 161.0), told(Station.ARIPAEV, 162.0, 262.0))
    val digest = Digest(LocalDate.of(2026, 11, 16), "2026-11-16T10:00:00Z", 263.0, "google/gemini-4-carbon", stories)
    expect(digestSummary(DigestShow.WEEK, digest, 1).status.station).toEqual(Station.ARIPAEV)
  }

  @Test
  fun `should list every station in the digest with the longest heard first`() {
    val mention = DigestMention("Hommikuprogramm", Station.SKYPLUS, LocalDate.of(2026, 11, 15))
    val stories = listOf(told(Station.KUKU, 0.0, 60.0, also = listOf(mention)), told(Station.ARIPAEV, 61.0, 161.0))
    val digest = Digest(LocalDate.of(2026, 11, 16), "2026-11-16T10:00:00Z", 162.0, "google/gemini-4-carbon", stories)
    expect(digestSummary(DigestShow.WEEK, digest, 1).stations).toEqual(listOf(Station.ARIPAEV, Station.KUKU, Station.SKYPLUS))
  }

  @Test
  fun `should list the shows the clips come from in digest order`() {
    val stories =
      listOf(
        told(Station.KUKU, 0.0, 60.0, "Sihik"),
        told(Station.ARIPAEV, 61.0, 161.0, "Kuum tool"),
        told(Station.KUKU, 162.0, 200.0, "Sihik"),
      )
    val digest = Digest(LocalDate.of(2026, 11, 16), "2026-11-16T10:00:00Z", 201.0, "google/gemini-4-carbon", stories)
    expect(digestSummary(DigestShow.WEEK, digest, 1).shows).toEqual(listOf("Sihik", "Kuum tool"))
  }

  @Test
  fun `should place a line after a removed ad break at its place in the digest`() {
    val lines = listOf(line(100.0, 140.0), line(200.0, 230.0))
    val audible = listOf(KeptSpan(0.0, 150.0), KeptSpan(200.0, 400.0))
    expect(shifted(lines, 100.0 to 250.0, audible, 600.0).map { it.start }).toEqual(listOf(600.0, 650.0))
  }

  @Test
  fun `should not carry a cut's review mark into the digest transcript`() {
    val split = Cut(CutKind.BREAK, "Reklaam", 120.0, 240.0, emptyList(), review = false, removed = true)
    val lines = listOf(line(100.0, 140.0).copy(split = split))
    expect(shifted(lines, 100.0 to 250.0, FULL, 0.0).map { it.split }).toEqual(listOf(null))
  }

  @Test
  fun `should find the moment in the show a clip starts at`() {
    expect(rawSeconds(50.0, listOf(KeptSpan(0.0, 40.0), KeptSpan(60.0, 100.0)))).toEqual(70.0)
  }

  @Test
  fun `should not list the show a story was taken from among the others that told it`() {
    val best = row(0.0, 600.0)
    val other = best.copy(id = "r2", programme = "Samost ja Aaspõllu", day = LocalDate.of(2026, 11, 15))
    val ranked = RankedStory(story("r1", listOf("r1", "r2", "r3"), 5, 5), best, listOf(best.copy(id = "r3"), other), 15)
    val clip = DigestClip(ranked, cutRecording(RECORDING, emptyList()), 30.0, 210.0, emptyList())
    expect(source(clip).also).toEqual(listOf(DigestMention("Samost ja Aaspõllu", Station.KUKU, LocalDate.of(2026, 11, 15))))
  }

  @Test
  fun `should lead a story with the jingle of its own programme`() {
    val newer = OWN.copy(programme = "Samost ja Aaspõllu", file = "kuku-samost-ja-aaspollu.m4a", savedAt = "2026-11-15T10:00:00Z")
    expect(led(listOf(clip(180.0)), listOf(newer, OWN)).map { it.ident }).toEqual(listOf(OWN))
  }

  @Test
  fun `should lead a story with the newest jingle of its station when its programme has none`() {
    val older = OWN.copy(programme = "Vox populi", savedAt = "2026-11-13T10:00:00Z")
    val newer = OWN.copy(programme = "Samost ja Aaspõllu", savedAt = "2026-11-15T10:00:00Z")
    expect(led(listOf(clip(180.0)), listOf(older, newer)).map { it.ident }).toEqual(listOf(newer))
  }

  @Test
  fun `should lead a story with silence when its station has no jingle`() {
    val other = OWN.copy(station = Station.ARIPAEV)
    expect(led(listOf(clip(180.0)), listOf(other)).map { it.ident }).toEqual(listOf(null))
  }

  @Test
  fun `should make room for the jingle before each later story it leads`() {
    val clips = listOf(clip(180.0).copy(ident = OWN), clip(180.0), clip(180.0).copy(ident = OWN))
    expect(gaps(clips)).toEqual(listOf(1.0, 5.4, 1.0))
  }

  @Test
  fun `should count the jingle before a clip toward the hour`() {
    val clips = listOf(clip(1800.0), clip(1795.0).copy(ident = OWN))
    expect(fitted(clips, DigestShow.WEEK.seconds).map { it.length }).toEqual(listOf(1800.0))
  }

  private fun clip(length: Double): DigestClip {
    val ranked = RankedStory(story("r1", listOf("r1"), 5, 5), row(0.0, 600.0), emptyList(), 15)
    return DigestClip(ranked, cutRecording(RECORDING, emptyList()), 0.0, length, emptyList())
  }

  private fun story(
    best: String,
    rows: List<String>,
    importance: Int,
    interest: Int,
    repeat: Boolean = false,
  ) = JudgedStory("Lugu $best", "Kokkuvõte", rows, best, "news", importance, interest, repeat, "Põhjus")

  private fun table(vararg programmes: String): Map<String, DigestRow> =
    programmes.mapIndexed { index, programme -> row(0.0, 600.0).copy(id = "r${index + 1}", programme = programme) }.associateBy { it.id }

  private fun row(
    start: Double,
    end: Double,
  ) = DigestRow("r1", RECORDING, "Keskpäevatund", Station.KUKU, LocalDate.of(2026, 11, 14), "Ust-Luga", null, start, end, end - start)

  private fun told(
    station: Station,
    start: Double,
    end: Double,
    programme: String = "Keskpäevatund",
    also: List<DigestMention> = emptyList(),
  ) = DigestStory(
    "Lugu",
    "Kokkuvõte",
    start,
    end,
    5,
    4,
    14,
    "news",
    "Põhjus",
    SectionSource(RECORDING, programme, station, LocalDate.of(2026, 11, 14), 0.0, also),
  )

  private fun line(
    start: Double,
    end: Double,
  ) = TranscriptLine(start, end, "Sõda on jõudnud lähedale.", emptyList())

  companion object {
    private val TALLINN = ZoneId.of("Europe/Tallinn")
    private val FULL = listOf(KeptSpan(0.0, 3600.0))
    private const val RECORDING = "2026-11-14-1200-keskpaevatund"
    private val OWN = Ident(Station.KUKU, "Keskpäevatund", "kuku-keskpaevatund.m4a", 5.0, "2026-11-14T10:00:00Z")
  }
}
