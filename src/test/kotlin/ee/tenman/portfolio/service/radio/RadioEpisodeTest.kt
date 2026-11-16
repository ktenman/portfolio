package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import kotlin.math.round

class RadioEpisodeTest {
  @Test
  fun `should title each join after a cut`() {
    val keep = listOf(761.5 to 2120.59, 2255.27 to 3085.3, 3197.32 to 3837.38)
    val topics =
      listOf(
        OutlineChapter(793.5, "Rahvusooper Estonia juhtimiskriis"),
        OutlineChapter(2257.7, "Üleminekud sotsidesse ja Eesti 200 kriis"),
        OutlineChapter(2711.8, "Umbusaldusavaldus ja sotside dilemma"),
        OutlineChapter(3202.2, "President Ülle Madise ja tema nõunikud"),
      )
    expect(chapters(keep, topics, PROGRAMME).map { round(it.first * 100) / 100 to it.second }).toEqual(
      listOf(
        0.0 to "Rahvusooper Estonia juhtimiskriis",
        1354.09 to "Cut 1 (22:39) – Üleminekud sotsidesse ja Eesti 200 kriis",
        1815.62 to "Umbusaldusavaldus ja sotside dilemma",
        2184.12 to "Cut 2 (36:29) – President Ülle Madise ja tema nõunikud",
      ),
    )
  }

  @Test
  fun `should map a moment inside a removed stretch to the seam`() {
    expect(episodeSeconds(50.0, listOf(0.0 to 40.0, 60.0 to 100.0))).toEqual(40.0)
  }

  @Test
  fun `should map a moment after a removed stretch past the seam`() {
    expect(episodeSeconds(70.0, listOf(0.0 to 40.0, 60.0 to 100.0))).toEqual(50.0)
  }

  @Test
  fun `should leave out a chapter that starts after the episode ends`() {
    expect(metadata(listOf(0.0 to "Soovide aeg", 596.67 to "Lõpusoov"), 596.666)).toEqual(
      ";FFMETADATA1\n[CHAPTER]\nTIMEBASE=1/1000\nSTART=0\nEND=596666\ntitle=Soovide aeg\n",
    )
  }
}
