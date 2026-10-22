package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class RadioSectionsTest {
  private val cut = Cut(CutKind.BREAK, "Ad break", 100.0, 160.0, emptyList(), review = false, removed = true)

  private val timeline =
    listOf(
      TimelineEntry(0.0, 100.0, "Ooperi tüli", false),
      TimelineEntry(100.0, 160.0, "Ad break", true),
      TimelineEntry(160.0, 300.0, "Šokolaadi hind", false),
    )

  private val audible = listOf(KeptSpan(0.0, 100.0), KeptSpan(160.0, 300.0))

  private fun line(start: Double) = TranscriptLine(start, start + 5, "Tere $start", emptyList())

  @Test
  fun `should put each line under the topic it starts in`() {
    val found = sections(timeline, listOf(cut), audible, listOf(line(10.0), line(170.0)), PROGRAMME)
    expect(found.map { section -> section.lines.map { it.start } }).toEqual(listOf(listOf(10.0), emptyList(), listOf(170.0)))
  }

  @Test
  fun `should give a cut section the index of its cut`() {
    expect(sections(timeline, listOf(cut), audible, emptyList(), PROGRAMME).map { it.cut }).toEqual(listOf(null, 0, null))
  }

  @Test
  fun `should give a topic its start in the episode`() {
    expect(sections(timeline, listOf(cut), audible, emptyList(), PROGRAMME).map { it.episodeStart }).toEqual(listOf(0.0, null, 100.0))
  }

  @Test
  fun `should give a kept cut its start in the episode`() {
    val kept = sections(timeline, listOf(cut.copy(removed = false)), listOf(KeptSpan(0.0, 300.0)), emptyList(), PROGRAMME)
    expect(kept.map { it.episodeStart }).toEqual(listOf(0.0, 100.0, 160.0))
  }

  @Test
  fun `should leave the episode start empty for a topic inside a gap in the episode`() {
    val found = sections(timeline, listOf(cut), listOf(KeptSpan(0.0, 90.0), KeptSpan(310.0, 400.0)), emptyList(), PROGRAMME)
    expect(found.last().episodeStart).toEqual(null)
  }

  @Test
  fun `should leave the episode start empty for a topic outside the episode`() {
    val found = sections(timeline, listOf(cut), listOf(KeptSpan(0.0, 100.0)), emptyList(), PROGRAMME)
    expect(found.last().episodeStart).toEqual(null)
  }

  @Test
  fun `should keep lines past the last end in the last section`() {
    expect(sections(timeline, listOf(cut), audible, listOf(line(300.0)), PROGRAMME).last().lines.size).toEqual(1)
  }

  @Test
  fun `should keep a section without lines`() {
    expect(sections(timeline, listOf(cut), audible, emptyList(), PROGRAMME).map { it.title })
      .toEqual(listOf("Ooperi tüli", "Ad break", "Šokolaadi hind"))
  }

  @Test
  fun `should title a part no topic covers with the programme`() {
    val untitled = listOf(TimelineEntry(0.0, 60.0, null, false))
    expect(sections(untitled, emptyList(), listOf(KeptSpan(0.0, 60.0)), emptyList(), PROGRAMME).single().title).toEqual(PROGRAMME)
  }
}
