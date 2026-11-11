package ee.tenman.portfolio.service.poff

import ee.tenman.portfolio.domain.PoffCredit
import ee.tenman.portfolio.domain.PoffFilm
import ee.tenman.portfolio.domain.PoffScreening
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.Instant

data class PoffListing(
  val id: Long,
  val slug: String,
)

private val duration = Regex("""(\d+)h&nbsp;(\d+)m""")

private val ticketId = Regex("""(\d+)\)\s*$""")

fun parseListing(html: String): List<PoffListing> =
  Jsoup.parse(html).select("a.card_film[id][href]").map {
    PoffListing(it.id().toLong(), it.attr("href").removePrefix("/en/film/").trim('/'))
  }

fun parseFilm(
  listing: PoffListing,
  html: String,
): PoffFilm {
  val sections = ownSections(Jsoup.parse(html).select("section"))
  val header = section(sections, "f_title_box")
  val heading =
    header?.selectFirst("h1.film_title_detailed")
      ?: throw IllegalStateException("Film page ${listing.slug} has no title")
  val info = pairs(header)
  val story = section(sections, "f_synopsis_box")
  val meta = meta(story)
  val runtime = pairs(section(sections, "f_runtime_box"))
  return PoffFilm(
    id = listing.id,
    slug = listing.slug,
    title = info["Local title"]?.text() ?: heading.text(),
    englishTitle = heading.text(),
    originalTitle = info["Original title"]?.text(),
    premiere = header?.selectFirst(".premiere_type")?.text()?.ifBlank { null },
    directors = names(info["Director"]),
    countries = names(info["Country"]),
    year = info["Year"]?.text()?.toIntOrNull(),
    minutes = runtime["Runtime"]?.let(::minutes),
    programmes = names(meta["Programme"]),
    genres = names(meta["Genre"]),
    subjects = names(meta["Subject"]),
    moods = names(meta["Mood"]),
    synopsis =
      story
      ?.select(".synopsis_gi p")
      ?.map { it.text() }
      ?.filter { it.isNotBlank() }
      .orEmpty(),
      languages = names(runtime["Language"]),
    subtitles = names(runtime["Subtitles"]),
    credits = pairs(section(sections, "f_cred_box")).map { (role, value) -> PoffCredit(role, names(value)) },
    stills = section(sections, "f_img_slider_box")?.select("img.d-block[src]")?.map { it.attr("src") }.orEmpty(),
    trailer = section(sections, "f_trailer_box")?.selectFirst("[data-video-id]")?.attr("data-video-id"),
    screenings = sections.flatMap { it.select("div.card_screening") }.map(::screening),
  )
}

private fun ownSections(sections: List<Element>): List<Element> {
  val titles = sections.withIndex().filter { it.value.hasClass("f_title_box") }.map { it.index }
  return if (titles.size < 2) sections else sections.subList(0, titles[1])
}

private fun section(
  sections: List<Element>,
  name: String,
): Element? = sections.firstOrNull { it.hasClass(name) }

private fun pairs(container: Element?): Map<String, Element> =
  container
    ?.select(".str_info_grid_3")
    ?.filter { it.childrenSize() >= 2 }
    ?.associate { it.child(0).text() to it.child(1) }
    .orEmpty()

private fun meta(container: Element?): Map<String, Element> =
  container
    ?.select(".str_info_meta_label")
    ?.mapNotNull { label -> label.nextElementSibling()?.let { label.text() to it } }
    ?.toMap()
    .orEmpty()

private fun names(value: Element?): List<String> =
  value
    ?.text()
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    .orEmpty()

private fun minutes(value: Element): Int? =
  duration.find(value.html())?.destructured?.let { (hours, minutes) -> hours.toInt() * 60 + minutes.toInt() }

private fun screening(card: Element): PoffScreening {
  val calendar =
    card.selectFirst("button.cal_add_inline")
      ?: throw IllegalStateException("Screening ${card.id()} has no calendar button")
  val id =
    calendar
      .attr("data-cal-uid")
      .removePrefix("screening-")
      .substringBefore("@")
      .toLongOrNull()
      ?: throw IllegalStateException("Screening at ${calendar.attr("data-cal-start")} has no id on its calendar button")
  val info = pairs(card)
  val ticket = card.selectFirst("a[onclick*=popupWidget]")
  val guests = info["Q&A guests"]?.text()
  val qaMinutes = info["Q&A duration"]?.let(::minutes)
  val city = calendar.attr("data-cal-location").substringAfterLast(",").trim()
  return PoffScreening(
    id = id,
    start = Instant.parse(calendar.attr("data-cal-start")),
    end = Instant.ofEpochMilli(card.attr("data-ends-at").toLong()),
    cinema =
      card
      .selectFirst(".screening_venue")
      ?.text()
      .orEmpty()
      .removeSuffix(", ${city.uppercase()}"),
      city = city,
    hall = card.selectFirst(".screening_hall")?.text().orEmpty(),
    code = info["Screening"]?.text()?.substringBefore(" / ")?.trim(),
    qa = guests != null || qaMinutes != null,
    qaMinutes = qaMinutes,
    guests = guests,
    ticket =
      ticket
      ?.attr("onclick")
      ?.let { ticketId.find(it) }
      ?.groupValues
      ?.get(1)
      ?.toLong(),
      soldOut = ticket?.text() == "Sold out",
  )
}
