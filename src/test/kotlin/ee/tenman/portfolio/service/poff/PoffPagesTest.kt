package ee.tenman.portfolio.service.poff

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.time.Instant

class PoffPagesTest {
  private val listing =
    """
    <div class="grid"><a class="card_film" id="9107" href="/en/film/i-am-not-a-tourist-i-live-here-and-tallinn-market"><h3>Tallinn Market</h3></a>
    <a class="card_film" id="8810" href="/en/film/beril_xxx"><h3>Beril_XXX</h3></a></div>
    """.trimIndent()

  private val page =
    """
    <section class="f_img_slider_box carousel slide"><img class="d-block container_contents_16_9" src="https://assets.poff.ee/img/F_1_Beril_XXX_big_16_9.jpeg" alt="Beril_XXX"></section>
    <section class="f_title_box"><div class="premiere_type"><a href="/en/search_film/?premieretypes=World%20premiere">World premiere</a></div><h1 class="film_title_detailed">Beril_XXX</h1><div class="str_info_blokk_1"><div class="str_info_grid_3"><div class="str_info_label">Local title</div><div class="str_info_value">Beril_XXX ehk Õhtu</div></div><div class="str_info_grid_3"><div class="str_info_label">Original title</div><div class="str_info_value">Beril_XXX orijinal</div></div><div class="str_info_grid_3"><div class="str_info_label">Director</div><div class="str_info_value clr_primary">Can Evrenol</div></div><div class="str_info_grid_3"><div class="str_info_label">Country</div><div class="str_info_value"><a href="/en/search_film/?countries=T%C3%BCrkiye">Türkiye</a></div></div><div class="str_info_grid_3"><div class="str_info_label">Year</div><div class="str_info_value">2026</div></div></div></section>
    <section class="f_synopsis_box"><div class="str_info_meta_gi"><div class="str_info_meta_item"><div class="str_info_meta_label dark_1">Festival</div><div class="str_info_meta_festival">30th Black Nights Film Festival</div><div class="str_info_meta_label dark_1">Programme</div><div class="str_info_meta_festival"><a href="/en/rebels-with-a-cause-competition">Rebels with a Cause Competition</a></div><div class="str_info_meta_label dark_1">Genre</div><div class="str_info_meta_festival"><a href="/en/search_film/?genres=Drama">Drama</a>,&#32;<a href="/en/search_film/?genres=Dramedy">Dramedy</a></div><div class="str_info_meta_label dark_1">Subject</div><div class="str_info_meta_festival"><a href="#">art</a>,&#32;<a href="#">love</a></div><div class="str_info_meta_label dark_1">Mood</div><div class="str_info_meta_festival"><a href="#">offbeat</a></div></div></div><div class="synopsis_gi"><p>Beril walks us through Istanbul.</p><p>Javier Garcia Puerto</p></div></section>
    <section class="f_cred_box"><div class="str_info_blokk_cred"><div class="str_info_grid_3"><div class="str_info_label">Director</div><div class="str_info_value">Can Evrenol</div></div><div class="str_info_grid_3"><div class="str_info_label">Cast</div><div class="str_info_value">Doga Lara, Burkut Kum</div></div></div></section>
    <section class="f_runtime_box"><div class="str_info_grid_3"><div class="str_info_label">Runtime</div><div class="str_info_value"><span id="dt_0.93"></span><script>document.getElementById("dt_0.93").parentElement.innerHTML="1h&nbsp;32m"</script></div></div><div class="str_info_grid_3"><div class="str_info_label">Language</div><div class="str_info_value"><a href="#">Turkish</a></div></div><div class="str_info_grid_3"><div class="str_info_label">Subtitles</div><div class="str_info_value"><a href="#">English</a></div></div></section>
    <section class="f_screenings_box"><div class="card-group m-o p-0">
    <div class="card card_screening rounded-0 border-0" id="screening_6688" data-ends-at="1794690720000"><div class="container_screening_location"><span class="screening_hall">9th Hall&#32;</span><span class="screening_venue"><a href="#">Apollo Kino Plaza</a></span><button class="cal_add_inline" type="button" data-cal-uid="screening-6688@poff.ee" data-cal-start="2026-11-14T19:15:00.000Z" data-cal-location="9th Hall, Apollo Kino Plaza, Tallinn"></button></div><div class="card-body bg_dark_3"><div class="screening_info_blokk"><div class="str_info_grid_3"><span class="screening_info_label">Screening</span><span class="screening_info_value">090921 / Beril_XXX</span></div><div class="str_info_grid_3"><span class="screening_info_label clr_primary">Q&amp;A guests</span><span class="screening_info_value">Can Evrenol (Director), Doga Lara (Cast)</span></div><div class="str_info_grid_3"><span class="screening_info_label">Q&amp;A duration</span><span class="screening_info_value"><span id="dt_0.98"></span><script>document.getElementById("dt_0.98").parentElement.innerHTML="0h&nbsp;20m"</script></span></div></div></div><div class="btn-group" role="group"><a class="btn btn_secondary active" onclick="popupWidget(20432,&#34;eng&#34;,512125)" role="button">Sold out</a></div></div>
    <div class="card card_screening rounded-0 border-0" id="screening_6959" data-ends-at="1794771420000"><div class="container_screening_location"><span class="screening_hall">2. Hall&#32;</span><span class="screening_venue"><a href="#">Apollo Kino Tasku, TARTU</a></span><button class="cal_add_inline" type="button" data-cal-uid="screening-6959@poff.ee" data-cal-start="2026-11-15T17:00:00.000Z" data-cal-location="2. Hall, Apollo Kino Tasku, TARTU, Tartu"></button></div><div class="card-body bg_dark_3"><div class="screening_info_blokk"><div class="str_info_grid_3"><span class="screening_info_label">Screening</span><span class="screening_info_value">091502 / Beril_XXX</span></div></div></div><div class="btn-group" role="group"><a class="btn btn_secondary active" onclick="popupWidget(20432,&#34;eng&#34;,514297)" role="button">Tickets</a></div></div>
    </div></section>
    <section class="f_trailer_box" id="trailer"><div class="yt_facade" data-video-id="NZGHgVd1T4A"></div></section>
    """.trimIndent()

  private val programme =
    """
    <section class="f_title_box"><h1 class="film_title_detailed">Shorts Animation Competition 1</h1></section>
    <section class="f_synopsis_box"></section>
    <section class="f_title_box"><h1 class="film_title_detailed">Boundaries</h1><div class="str_info_grid_3"><div class="str_info_label">Year</div><div class="str_info_value">2025</div></div></section>
    <section class="f_runtime_box"><div class="str_info_grid_3"><div class="str_info_label">Runtime</div><div class="str_info_value"><script>x.innerHTML="0h&nbsp;12m"</script></div></div></section>
    """.trimIndent()

  private val beril = PoffListing(8810, "beril_xxx")

  private fun film() = parseFilm(beril, page)

  @Test
  fun `reads every film card of the listing`() {
    expect(parseListing(listing)).toContainExactly(
      PoffListing(9107, "i-am-not-a-tourist-i-live-here-and-tallinn-market"),
      PoffListing(8810, "beril_xxx"),
    )
  }

  @Test
  fun `reads the Estonian, English and original titles`() {
    expect(film().let { listOf(it.title, it.englishTitle, it.originalTitle) })
      .toEqual(listOf("Beril_XXX ehk Õhtu", "Beril_XXX", "Beril_XXX orijinal"))
  }

  @Test
  fun `reads the length written by an inline script`() {
    expect(film().minutes).toEqual(92)
  }

  @Test
  fun `reads the cast from the credits`() {
    expect(film().credits.single { it.role == "Cast" }.names).toContainExactly("Doga Lara", "Burkut Kum")
  }

  @Test
  fun `reads the premiere type`() {
    expect(film().premiere).toEqual("World premiere")
  }

  @Test
  fun `reads the genres and programmes`() {
    expect(film().let { it.genres + it.programmes }).toContainExactly("Drama", "Dramedy", "Rebels with a Cause Competition")
  }

  @Test
  fun `reads a sold out screening with its ticket id`() {
    expect(film().screenings.first().let { listOf(it.id, it.ticket, it.soldOut, it.start, it.end, it.code) })
      .toEqual(listOf(6688L, 512125L, true, Instant.parse("2026-11-14T19:15:00Z"), Instant.ofEpochMilli(1794690720000), "090921"))
  }

  @Test
  fun `reads the id of a screening from its calendar button when the card has none`() {
    expect(parseFilm(beril, page.replace(""" id="screening_6959"""", "")).screenings.last().id).toEqual(6959L)
  }

  @Test
  fun `reads the Q&A guests and length of a screening`() {
    expect(film().screenings.first().let { listOf(it.qa, it.qaMinutes, it.guests) })
      .toEqual(listOf(true, 20, "Can Evrenol (Director), Doga Lara (Cast)"))
  }

  @Test
  fun `reads a screening without Q&A as having none`() {
    expect(film().screenings.last().let { listOf(it.qa, it.qaMinutes, it.guests, it.soldOut) }).toEqual(listOf(false, null, null, false))
  }

  @Test
  fun `takes the city from the calendar location and drops TARTU from the cinema`() {
    expect(film().screenings.last().let { listOf(it.cinema, it.city, it.hall) }).toEqual(listOf("Apollo Kino Tasku", "Tartu", "2. Hall"))
  }

  @Test
  fun `reads the still and trailer of the film`() {
    expect(film().let { it.stills + it.trailer }).toContainExactly("https://assets.poff.ee/img/F_1_Beril_XXX_big_16_9.jpeg", "NZGHgVd1T4A")
  }

  @Test
  fun `falls back to the English title when the page has no local title`() {
    expect(parseFilm(beril, programme).title).toEqual("Shorts Animation Competition 1")
  }

  @Test
  fun `reads a film without a year as having none`() {
    expect(parseFilm(beril, programme).year).toEqual(null)
  }

  @Test
  fun `reads no length for a shorts programme from the first short`() {
    expect(parseFilm(beril, programme).minutes).toEqual(null)
  }
}
