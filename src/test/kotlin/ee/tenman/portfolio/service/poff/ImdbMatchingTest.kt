package ee.tenman.portfolio.service.poff

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.imdb.ImdbSuggestion
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ImdbMatchingTest {
  private val beril =
    poffFilm(8810, title = "Beril_XXX", englishTitle = "Beril_XXX", minutes = 92, cast = listOf("Doga Lara", "Burkut Kum"))

  private fun candidate(
    id: String = "tt50340469",
    title: String = "Beril_XXX",
    kind: String = "movie",
    stars: String = "Doga Lara Akkaya, Burkut Kum",
    year: Int? = 2026,
  ) = ImdbSuggestion(id, title, kind, stars, year)

  @Test
  fun `scores Beril_XXX twelve against its IMDb entry`() {
    expect(score(beril, candidate())).toEqual(12)
  }

  @Test
  fun `rejects a candidate two years apart`() {
    expect(score(beril, candidate(year = 2024))).toEqual(null)
  }

  @Test
  fun `rejects a candidate without a year`() {
    expect(score(beril, candidate(year = null))).toEqual(null)
  }

  @Test
  fun `rejects a TV series`() {
    expect(score(beril, candidate(kind = "tvSeries"))).toEqual(null)
  }

  @Test
  fun `rejects a person`() {
    expect(score(beril, candidate(id = "nm0000001"))).toEqual(null)
  }

  @Test
  fun `matches an actor whose IMDb name has an extra surname`() {
    expect(score(beril, candidate(title = "Teine film", stars = "Doga Lara Akkaya"))).toEqual(7)
  }

  @Test
  fun `finds Kõlakamber by its title without accents`() {
    expect(normalise("Kõlakamber: Öö_2")).toEqual("kolakamber oo 2")
  }

  @Test
  fun `prefers the short for a short film`() {
    val film = poffFilm(1, title = "Tuul", englishTitle = "Wind", minutes = 15)
    expect(
      bestMatch(film, listOf(candidate("tt1", "Wind", "movie", ""), candidate("tt2", "Wind", "short", ""))),
    ).toEqual(ImdbMatch("tt2", 7))
  }

  @Test
  fun `finds no match below four points`() {
    expect(bestMatch(beril, listOf(candidate(kind = "video", stars = "", year = 2025)))).toEqual(null)
  }

  @Test
  fun `rejects a candidate that shares neither a title nor an actor`() {
    expect(score(beril, candidate(title = "Teine film", stars = "Mari Mägi"))).toEqual(null)
  }

  @Test
  fun `reads overrides that set and block an IMDb id`() {
    expect(readOverrides("film_id\timdb_id\ttitle\n8810\t-\tBeril_XXX\n9107\ttt123\tTallinna turg\n"))
      .toEqual(mapOf(8810L to null, 9107L to "tt123"))
  }

  @Test
  fun `reads the rating and votes of the matched titles only`() {
    val file = ratingsFile("tt0000001\t5.6\t2183", "tt9395802\t7.2\t4184")
    expect(readRatings(file, setOf("tt9395802", "tt50340469"))).toEqual(
      mapOf(
      "tt9395802" to ImdbVotes(BigDecimal("7.2"), 4184),
      ),
        )
  }
}
