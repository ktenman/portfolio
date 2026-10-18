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
    expect(chapters(keep, topics).map { round(it.first * 100) / 100 to it.second }).toEqual(
      listOf(
        0.0 to "Rahvusooper Estonia juhtimiskriis",
        1354.09 to "Lõige 1 (22:39) – Üleminekud sotsidesse ja Eesti 200 kriis",
        1815.62 to "Umbusaldusavaldus ja sotside dilemma",
        2184.12 to "Lõige 2 (36:29) – President Ülle Madise ja tema nõunikud",
      ),
    )
  }
}
