package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Station
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

class RadioSearchTest {
  private fun recording(
    name: String,
    vararg said: Pair<Double, String>,
    edit: EditList? = null,
    startedAt: Double = 1792230900.0,
    station: Station = Station.KUKU,
  ): IndexedRecording =
    indexed(
      name,
      RecordingStatus(RecordingState.CAPTURED, startedAt, 4200.0, programmes = listOf(PROGRAMME), station = station),
      Transcript(said.map { (start, text) -> TranscriptSegment(start, start + 2.0, text) }, emptyList()),
      edit,
    )

  private fun daily(
    days: IntRange,
    sung: Int? = null,
  ): List<IndexedRecording> =
    days.map { day ->
      val edit = editList(music = listOf(KeptSpan(0.0, 100.0))).takeIf { day == sung }
      recording("2026-10-%02d".format(day), 12.0 to "Valimised", edit = edit, startedAt = 1790000000.0 + day * 86400)
    }

  private fun bracketed(parts: List<TextPart>): String = parts.joinToString("") { if (it.mark) "[${it.text}]" else it.text }

  private fun shown(found: RadioSearch): List<String> =
    found.groups.flatMap { group -> group.lines.map { "${group.name} ${it.start} ${bracketed(it.text)}" } }

  @Test
  fun `should find a word whatever its case and diacritics`() {
    val found = search(listOf(recording("2026-10-17", 12.0 to "Tõukerattad on tänavatel")), "TOUKERATTAD", null)
    expect(shown(found)).toEqual(listOf("2026-10-17 12.0 [Tõukerattad] on tänavatel"))
  }

  @Test
  fun `should find a word pasted with its accents as separate marks`() {
    expect(search(listOf(recording("2026-10-17", 12.0 to "Tõnis rääkis")), "To\u0303nis", null).lines).toEqual(1)
  }

  @Test
  fun `should rank the exact word above the words that start with it`() {
    val exact = recording("2026-10-17", 12.0 to "Tänavad olid tühjad", startedAt = 1792230900.0)
    val longer = recording("2026-10-24", 12.0 to "Tänavadki olid tühjad", startedAt = 1792835700.0)
    expect(search(listOf(longer, exact), "tanavad", null).groups.map { it.name }).toEqual(listOf("2026-10-17", "2026-10-24"))
  }

  @Test
  fun `should find the words that start with a stem of four letters or more`() {
    val found = search(listOf(recording("2026-10-17", 12.0 to "Valimised tulevad", 20.0 to "Valimistel räägiti")), "valimis", null)
    expect(found.lines).toEqual(2)
  }

  @Test
  fun `should not stretch a word of three letters to the words that start with it`() {
    expect(search(listOf(recording("2026-10-17", 12.0 to "Maailm muutub")), "maa", null).lines).toEqual(0)
  }

  @Test
  fun `should rank the word as typed above its spelling without diacritics`() {
    val typed = recording("2026-10-17", 12.0 to "Tõukerattad on tänavatel", startedAt = 1792230900.0)
    val plain = recording("2026-10-24", 12.0 to "Toukerattad on tänavatel", startedAt = 1792835700.0)
    expect(search(listOf(plain, typed), "tõukerattad", null).groups.map { it.name }).toEqual(listOf("2026-10-17", "2026-10-24"))
  }

  @Test
  fun `should find the spelling the recognizer gives a name`() {
    val found = search(listOf(recording("2026-10-17", 12.0 to "Antropik teatas uuest mudelist")), "Anthropic", null)
    expect(shown(found)).toEqual(listOf("2026-10-17 12.0 [Antropik] teatas uuest mudelist"))
  }

  @Test
  fun `should suggest close words when nobody said the word`() {
    val found = search(listOf(recording("2026-10-17", 12.0 to "Valised need on", 20.0 to "Valised jälle")), "valimised", null)
    expect(found.suggestions).toEqual(listOf("valised"))
  }

  @Test
  fun `should not suggest a stop word`() {
    val found = search(listOf(recording("2026-10-17", 12.0 to "Seda ma ei tea", 20.0 to "Seda küll")), "sedda", null)
    expect(found.suggestions).toEqual(emptyList())
  }

  @Test
  @Timeout(3)
  fun `should answer a very long unknown word quickly`() {
    val said = (1..40_000).joinToString(" ") { "sõna$it" }
    val word = ('a'..'z').joinToString("") { a -> ('a'..'z').joinToString("") { b -> ('a'..'z').joinToString("") { "$a$b$it" } } }
    expect(search(listOf(recording("2026-10-17", 12.0 to said)), word, null).lines).toEqual(0)
  }

  @Test
  fun `should not find a word that only looks alike`() {
    expect(search(listOf(recording("2026-10-17", 12.0 to "Valised need on")), "valimised", null).lines).toEqual(0)
  }

  @Test
  fun `should still find the nouns of a question`() {
    val found = search(listOf(recording("2026-10-17", 12.0 to "Tehisaru areneb kiiresti")), "mis on tehisaru?", null)
    expect(listOf(found.lines, found.partial)).toEqual(listOf(1, false))
  }

  @Test
  fun `should not search a query of stop words`() {
    val found = search(listOf(recording("2026-10-17", 12.0 to "Kes oli seal?")), "kes oli", null)
    expect(found).toEqual(RadioSearch(searchable = false))
  }

  @Test
  fun `should search a word that becomes a stop word only without its diacritics`() {
    val found = search(listOf(recording("2026-10-17", 12.0 to "Õli hind tõusis")), "õli", null)
    expect(shown(found)).toEqual(listOf("2026-10-17 12.0 [Õli] hind tõusis"))
  }

  @Test
  fun `should keep a word of two capitals as a search word`() {
    expect(search(listOf(recording("2026-10-17", 12.0 to "AI muudab tööturgu")), "AI", null).lines).toEqual(1)
  }

  @Test
  fun `should mark a line cut from the episode`() {
    val cut =
      recording("2026-10-17", 50.0 to "Valimised on täna", 150.0 to "Valimised reklaamis", edit = editList(listOf(KeptSpan(0.0, 100.0))))
    expect(
      search(listOf(cut), "valimised", null)
      .groups
      .single()
      .lines
      .map { it.kept },
        ).toEqual(listOf(true, false))
  }

  @Test
  fun `should mark a line that a removed cut starts inside as cut from the episode`() {
    val ending = editList(listOf(KeptSpan(0.0, 51.0)), listOf(Boundary("show ends", 51.0, "digital silence", false)))
    val found = search(listOf(recording("2026-10-17", 50.0 to "Ja nüüd väike paus, ostke", edit = ending)), "ostke", null)
    expect(
      found.groups
      .single()
      .lines
      .single()
      .kept,
        ).toEqual(false)
  }

  @Test
  fun `should leave out a sung line`() {
    val sung = recording("2026-10-17", 150.0 to "Valimised laulus", edit = editList(music = listOf(KeptSpan(140.0, 160.0))))
    expect(search(listOf(sung), "valimised", null).lines).toEqual(0)
  }

  @Test
  fun `should settle for most of the words when no line has them all`() {
    val found =
      search(listOf(recording("2026-10-17", 12.0 to "Valimised tulevad", 20.0 to "Eelarve on paigas")), "valimised eelarve", null)
    expect(listOf(found.lines, found.partial)).toEqual(listOf(2, true))
  }

  @Test
  fun `should show the best four lines of a recording in time order`() {
    val said =
      arrayOf(
        10.0 to "Valimisedki",
        20.0 to "Valimisedki",
        30.0 to "Valimisedki",
        40.0 to "Valimisedki",
        50.0 to "Valimised",
        60.0 to "Valimised",
      )
    val group = search(listOf(recording("2026-10-17", *said)), "valimised", null).groups.single()
    expect(listOf(group.lines.map { it.start }, group.more)).toEqual(listOf(listOf(10.0, 20.0, 50.0, 60.0), 2))
  }

  @Test
  fun `should page the recordings after the score, start and name in the cursor`() {
    val found = search(daily(1..11), "valimised", "3.5 1.7901728E9 2026-10-02")
    expect(listOf(found.groups.map { it.name }, found.recordings, found.next)).toEqual(listOf(listOf("2026-10-01"), 11, null))
  }

  @Test
  fun `should find no recordings after a cursor it cannot read`() {
    val found = search(daily(1..11), "valimised", "2026-10-02")
    expect(listOf(found.groups, found.next)).toEqual(listOf(emptyList<LineGroup>(), null))
  }

  @Test
  fun `should neither repeat nor skip a recording when the one in the cursor turns out sung between two pages`() {
    val first = search(daily(1..11), "valimised", null)
    val second = search(daily(1..11, sung = 2), "valimised", first.next)
    expect((first.groups + second.groups).map { it.name }.filter { it != "2026-10-02" })
      .toEqual((11 downTo 1).filter { it != 2 }.map { "2026-10-%02d".format(it) })
  }

  @Test
  fun `should neither repeat nor skip a recording when one is added between two pages`() {
    val first = search(daily(1..11), "valimised", null)
    val second = search(daily(1..12), "valimised", first.next)
    expect((first.groups + second.groups).map { it.name }).toEqual((11 downTo 1).map { "2026-10-%02d".format(it) })
  }

  @Test
  fun `should carry the station of the recording on its lines and topics`() {
    val topical = editList(topics = listOf(EpisodeChapter(0.0, "Valimised")))
    val found = search(listOf(recording("2026-10-17", 12.0 to "Valimised", edit = topical, station = Station.ARIPAEV)), "valimised", null)
    expect(found.groups.single().station to found.topics.single().station).toEqual(Station.ARIPAEV to Station.ARIPAEV)
  }

  @Test
  fun `should list the topics on the first page only`() {
    val topical = editList(topics = listOf(EpisodeChapter(0.0, "Valimised")))
    val days = (1..11).map { recording("2026-10-%02d".format(it), 12.0 to "Valimised", edit = topical) }
    expect(search(days, "valimised", search(days, "valimised", null).next).topics).toEqual(emptyList())
  }

  @Test
  fun `should find a topic by its title and the host of the show`() {
    val hosted = editList(topics = listOf(EpisodeChapter(0.0, "Eelarve", summary = "Riigi rahast")), hosts = listOf("Tiiu Näidis"))
    val topic = search(listOf(recording("2026-10-17", edit = hosted)), "näidis eelarve", null).topics.single()
    expect(listOf(bracketed(topic.title), bracketed(topic.summary))).toEqual(listOf("[Eelarve]", "Riigi rahast"))
  }

  @Test
  fun `should not list a topic that has only some of the words`() {
    val hosted = editList(topics = listOf(EpisodeChapter(0.0, "Eelarve")))
    expect(search(listOf(recording("2026-10-17", edit = hosted)), "eelarve pension", null).topics).toEqual(emptyList())
  }

  @Test
  fun `should count the topics beyond the first five`() {
    val many = editList(topics = (0..5).map { EpisodeChapter(it * 600.0, "Eelarve $it") })
    val found = search(listOf(recording("2026-10-17", edit = many)), "eelarve", null)
    expect(listOf(found.topics.size, found.moreTopics)).toEqual(listOf(5, 1))
  }

  @Test
  fun `should open a topic where its part of the episode starts`() {
    val opening =
      editList(
        boundaries = listOf(Boundary("show starts", 100.0, "digital silence", false)),
        topics = listOf(EpisodeChapter(90.0, "Rahvusooper")),
      )
    expect(search(listOf(recording("2026-10-17", edit = opening)), "rahvusooper", null).topics.single().start).toEqual(100.0)
  }

  @Test
  fun `should find a topic by its summary when an earlier topic has the same title`() {
    val chapters = listOf(EpisodeChapter(0.0, "Eelarve", summary = "Maksud"), EpisodeChapter(1200.0, "Eelarve", summary = "Pensionid"))
    val found = search(listOf(recording("2026-10-17", edit = editList(topics = chapters))), "pensionid", null)
    expect(found.topics.map { it.start }).toEqual(listOf(1200.0))
  }

  @Test
  fun `should list a topic once when a cut splits it`() {
    val paused =
      editList(
        boundaries =
          listOf(
            Boundary("break 1 starts", 600.0, "digital silence", false),
            Boundary("break 1 ends", 700.0, "digital silence", false),
          ),
        topics = listOf(EpisodeChapter(0.0, "Eelarve")),
      )
    expect(search(listOf(recording("2026-10-17", edit = paused)), "eelarve", null).topics.map { it.start }).toEqual(listOf(0.0))
  }

  @Test
  fun `should cut a long line around the first word found`() {
    val text = "Alguses ${"sõna ".repeat(80)}valimised ${"lõpp ".repeat(60)}"
    val line =
      search(listOf(recording("2026-10-17", 12.0 to text)), "valimised", null)
      .groups
      .single()
      .lines
      .single()
    expect(bracketed(line.text)).toEqual("… ${"sõna ".repeat(11)}[valimised] ${"lõpp ".repeat(33)}lõpp …")
  }
}
