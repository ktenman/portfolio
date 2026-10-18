package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class RadioCutsTest {
  private val machine = listOf(KeptSpan(761.5, 2120.0), KeptSpan(2255.0, 3025.0), KeptSpan(3137.0, 3837.0))

  private fun boundary(
    name: String,
    seconds: Double,
    method: String = "digital silence",
  ) = Boundary(name, seconds, method, method == "transcript only")

  private fun edit(
    keep: List<KeptSpan> = machine,
    last: Double = 3837.0,
  ) = EditList(
    keep = keep,
    durationSeconds = 2512.5,
    parts = listOf(RecordingPart("raw.mp3", 0.0)),
    boundaries =
      listOf(
        boundary("show starts", 761.5),
        boundary("break 1 starts", 2120.0, "level dip"),
        boundary("break 1 ends", 2255.0, "station jingle, r=0.93"),
        boundary("break 2 starts", 3025.0),
        boundary("break 2 ends", 3137.0, "transcript only"),
        boundary("show ends", last),
      ),
    hosts = emptyList(),
    outline = OutlineSummary(null, 3, 3, 3),
    chapters = emptyList(),
    reviewed = false,
    renderedAt = "2026-10-17T10:20:00Z",
    topics =
      listOf(
        EpisodeChapter(760.0, "Rahvusooperi tüli"),
        EpisodeChapter(2700.0, "Mis toimub poliitikas?"),
        EpisodeChapter(3400.0, "President Ülle Madise"),
      ),
  )

  @Test
  fun `should find the cut before the show, each ad break and the cut after the show`() {
    expect(cuts(edit(), 4200.0).map { Triple(it.kind, it.start, it.end) }).toEqual(
      listOf(
        Triple(CutKind.BEFORE, 0.0, 761.5),
        Triple(CutKind.BREAK, 2120.0, 2255.0),
        Triple(CutKind.BREAK, 3025.0, 3137.0),
        Triple(CutKind.AFTER, 3837.0, 4200.0),
      ),
    )
  }

  @Test
  fun `should give the methods that placed both boundaries of a cut`() {
    expect(cuts(edit(), 4200.0)[1].methods).toEqual(listOf("level dip", "station jingle, r=0.93"))
  }

  @Test
  fun `should flag a cut with a boundary placed by the transcript alone for review`() {
    expect(cuts(edit(), 4200.0).map { it.review }).toEqual(listOf(false, false, true, false))
  }

  @Test
  fun `should drop a cut of no length`() {
    expect(cuts(edit(last = 4200.0), 4200.0).map { it.kind }).toEqual(listOf(CutKind.BEFORE, CutKind.BREAK, CutKind.BREAK))
  }

  @Test
  fun `should find no cut after the show when the show has no end`() {
    val opened = edit().copy(boundaries = listOf(boundary("show starts", 761.5)))
    expect(cuts(opened, 4200.0).map { it.kind }).toEqual(listOf(CutKind.BEFORE))
  }

  @Test
  fun `should mark every machine suggestion removed under the machine keep list`() {
    expect(cuts(edit(), 4200.0).map { it.removed }).toEqual(listOf(true, true, true, true))
  }

  @Test
  fun `should mark a break kept when the reviewed keep list covers it`() {
    val keep = listOf(KeptSpan(761.5, 3025.0), KeptSpan(3137.0, 3837.0))
    expect(cuts(edit(keep), 4200.0).map { it.removed }).toEqual(listOf(true, false, true, true))
  }

  @Test
  fun `should give back the machine keep list when every cut is removed`() {
    expect(keep(cuts(edit(), 4200.0), listOf(0, 1, 2, 3), 4200.0)).toEqual(machine)
  }

  @Test
  fun `should merge the neighbours of a kept break into one span`() {
    expect(keep(cuts(edit(), 4200.0), listOf(0, 2, 3), 4200.0)).toEqual(
      listOf(KeptSpan(761.5, 3025.0), KeptSpan(3137.0, 3837.0)),
    )
  }

  @Test
  fun `should keep the whole recording when no cut is removed`() {
    expect(keep(cuts(edit(), 4200.0), emptyList(), 4200.0)).toEqual(listOf(KeptSpan(0.0, 4200.0)))
  }

  @Test
  fun `should reject a cut number the recording does not have`() {
    expect { keep(cuts(edit(), 4200.0), listOf(4), 4200.0) }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should quote the last line before a cut and the first line after it`() {
    val lines =
      listOf(
        TranscriptLine(2100.0, 2119.0, "Ja siinkohal väike paus.", emptyList()),
        TranscriptLine(2130.0, 2140.0, "Ostke Ülemiste kaubamajast!", emptyList()),
        TranscriptLine(2256.0, 2270.0, "Ja Keskpäevatund jätkab.", emptyList()),
      )
    val found = context(cuts(edit(), 4200.0)[1], lines)
    expect(found.before?.text to found.after?.text).toEqual("Ja siinkohal väike paus." to "Ja Keskpäevatund jätkab.")
  }

  @Test
  fun `should quote nothing before the cut that opens the recording`() {
    val lines = listOf(TranscriptLine(10.0, 20.0, "Uudised.", emptyList()))
    expect(context(cuts(edit(), 4200.0)[0], lines).before).toEqual(null)
  }

  @Test
  fun `should list the cuts and the topics in recording order`() {
    expect(timeline(edit(), 4200.0).map { Triple(it.start, it.title, it.cut) }).toEqual(
      listOf(
        Triple(0.0, "Enne saadet", true),
        Triple(761.5, "Rahvusooperi tüli", false),
        Triple(2120.0, "Reklaamipaus", true),
        Triple(2255.0, "Rahvusooperi tüli", false),
        Triple(2700.0, "Mis toimub poliitikas?", false),
        Triple(3025.0, "Reklaamipaus", true),
        Triple(3137.0, "Mis toimub poliitikas?", false),
        Triple(3400.0, "President Ülle Madise", false),
        Triple(3837.0, "Pärast saadet", true),
      ),
    )
  }

  @Test
  fun `should show a recording without an edit list as one part`() {
    expect(timeline(null, 60.0)).toEqual(listOf(TimelineEntry(0.0, 60.0, "Keskpäevatund", false)))
  }
}
