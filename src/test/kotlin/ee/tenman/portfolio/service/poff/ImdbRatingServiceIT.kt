package ee.tenman.portfolio.service.poff

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.PoffImdb
import ee.tenman.portfolio.repository.PoffImdbRepository
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.wiremock.spring.InjectWireMock
import java.math.BigDecimal
import java.time.Instant

@IntegrationTest
class ImdbRatingServiceIT {
  @Resource
  private lateinit var imdbRatingService: ImdbRatingService

  @Resource
  private lateinit var poffImdbRepository: PoffImdbRepository

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  private fun serve(vararg lines: String) {
    wireMockServer.stubFor(get(urlPathEqualTo("/title.ratings.tsv.gz")).willReturn(aResponse().withBody(ratingsFile(*lines))))
  }

  private fun matched(
    film: Long,
    imdb: String?,
    rating: BigDecimal? = null,
  ) = poffImdbRepository.save(PoffImdb(film, imdb, 9, rating, rating?.let { 50 }, Instant.parse("2026-11-10T08:00:00Z")))

  private fun ratings() = poffImdbRepository.findAll().sortedBy { it.filmId }.map { it.rating?.toPlainString() to it.votes }

  @Test
  fun `stores the rating of every matched film`() {
    matched(1, "tt9395802")
    matched(2, "tt50340469")
    serve("tt0000001\t5.6\t2183", "tt9395802\t7.2\t4184", "tt50340469\t6.9\t87")
    imdbRatingService.refresh()
    expect(ratings()).toEqual(listOf("7.2" to 4184, "6.9" to 87))
  }

  @Test
  fun `clears the rating of a title the file no longer lists`() {
    matched(1, "tt9395802", BigDecimal("7.0"))
    serve("tt0000001\t5.6\t2183")
    imdbRatingService.refresh()
    expect(ratings()).toEqual(listOf<Pair<String?, Int?>>(null to null))
  }

  @Test
  fun `leaves a film without a match unrated`() {
    matched(1, null)
    serve("tt9395802\t7.2\t4184")
    imdbRatingService.refresh()
    expect(ratings()).toEqual(listOf<Pair<String?, Int?>>(null to null))
  }
}
