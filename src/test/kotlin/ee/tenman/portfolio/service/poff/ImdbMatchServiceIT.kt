package ee.tenman.portfolio.service.poff

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.PoffFilm
import ee.tenman.portfolio.imdb.ImdbSuggestionClient
import ee.tenman.portfolio.repository.PoffImdbRepository
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource
import org.wiremock.spring.InjectWireMock
import java.time.Instant

@IntegrationTest
class ImdbMatchServiceIT {
  @Resource
  private lateinit var imdbSuggestionClient: ImdbSuggestionClient

  @Resource
  private lateinit var poffImdbRepository: PoffImdbRepository

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  private val beril = poffFilm(8810, title = "Beril_XXX", englishTitle = "Beril_XXX", cast = listOf("Doga Lara"))

  private val found =
    """{"d":[{"id":"nm1","l":"Beril Xxx"},{"id":"tt50340469","l":"Beril_XXX","qid":"movie","s":"Doga Lara Akkaya, Burkut Kum","y":2026}],"q":"beril xxx"}"""

  private fun service(
    at: String = "2026-11-10T08:00:00Z",
    overrides: String = "film_id\timdb_id\ttitle\n",
  ) = ImdbMatchService(
    imdbSuggestionClient,
    poffImdbRepository,
    tallinnClock(at),
    ByteArrayResource(overrides.toByteArray()),
  )

  private fun respond(
    body: String,
    status: Int = 200,
    type: String = "application/json",
  ) {
    wireMockServer.stubFor(
      get(urlPathMatching("/suggestion/x/.*")).willReturn(aResponse().withStatus(status).withHeader("Content-Type", type).withBody(body)),
    )
  }

  private fun row(film: PoffFilm = beril) = poffImdbRepository.findAll().firstOrNull { it.filmId == film.id }

  @Test
  fun `stores the best match of a film`() {
    respond(found)
    service().match(listOf(beril))
    expect(row()?.let { it.imdbId to it.score }).toEqual("tt50340469" to 9)
  }

  @Test
  fun `counts the films it newly matched`() {
    respond(found)
    expect(service().match(listOf(beril, poffFilm(1, year = null)))).toEqual(1)
  }

  @Test
  fun `matches a film listed twice once`() {
    respond(found)
    expect(service().match(listOf(beril, beril))).toEqual(1)
  }

  @Test
  fun `does not search a film again once it has a match`() {
    respond(found)
    service().match(listOf(beril))
    wireMockServer.resetRequests()
    service("2026-11-12T08:00:00Z").match(listOf(beril))
    expect(wireMockServer.findAll(getRequestedFor(urlPathMatching("/suggestion/x/.*"))).size).toEqual(0)
  }

  @Test
  fun `stores a film without a match as tried`() {
    respond("""{"d":[],"q":"beril xxx"}""")
    service().match(listOf(beril))
    expect(row()?.let { it.imdbId to it.triedAt }).toEqual(null to Instant.parse("2026-11-10T08:00:00Z"))
  }

  @Test
  fun `writes no row when the search answers with an error`() {
    respond("Service Unavailable", 503, "text/plain")
    service().match(listOf(beril))
    expect(row()).toEqual(null)
  }

  @Test
  fun `stops searching after three films in a row fail`() {
    respond("Service Unavailable", 503, "text/plain")
    wireMockServer.resetRequests()
    service().match((1L..5L).map { poffFilm(it) })
    expect(wireMockServer.findAll(getRequestedFor(urlPathMatching("/suggestion/x/.*"))).size).toEqual(3)
  }

  @Test
  fun `keeps searching when a match comes between failed searches`() {
    respond("Service Unavailable", 503, "text/plain")
    wireMockServer.stubFor(get(urlPathMatching("/suggestion/x/[Bb]eril.*")).willReturn(okJson(found)))
    val second = poffFilm(8811, title = "Beril_XXX", englishTitle = "Beril_XXX", cast = listOf("Doga Lara"))
    expect(service().match(listOf(poffFilm(1), beril, poffFilm(2), poffFilm(3), second, poffFilm(4)))).toEqual(2)
  }

  @Test
  fun `writes no row when the search answers with a web page`() {
    respond("<html><body>Are you a robot?</body></html>", 200, "text/html")
    service().match(listOf(beril))
    expect(row()).toEqual(null)
  }

  @Test
  fun `waits a day before trying a film without a match again`() {
    respond("""{"d":[]}""")
    service("2026-11-10T08:00:00Z").match(listOf(beril))
    respond(found)
    service("2026-11-11T07:00:00Z").match(listOf(beril))
    expect(row()?.imdbId).toEqual(null)
  }

  @Test
  fun `tries a film without a match again after a day`() {
    respond("""{"d":[]}""")
    service("2026-11-10T08:00:00Z").match(listOf(beril))
    respond(found)
    service("2026-11-11T08:01:00Z").match(listOf(beril))
    expect(row()?.imdbId).toEqual("tt50340469")
  }

  @Test
  fun `lets an override win over the search`() {
    respond(found)
    service(overrides = "film_id\timdb_id\ttitle\n8810\ttt0000099\tBeril_XXX\n").match(listOf(beril))
    expect(row()?.imdbId).toEqual("tt0000099")
  }

  @Test
  fun `lets an override block a match found earlier`() {
    respond(found)
    service().match(listOf(beril))
    service(overrides = "film_id\timdb_id\ttitle\n8810\t-\tBeril_XXX\n").match(listOf(beril))
    expect(row()?.imdbId).toEqual(null)
  }

  @Test
  fun `searches a film again once its override is removed`() {
    respond(found)
    service(overrides = "film_id\timdb_id\ttitle\n8810\ttt0000099\tBeril_XXX\n").match(listOf(beril))
    service("2026-11-12T08:00:00Z").match(listOf(beril))
    expect(row()?.imdbId).toEqual("tt50340469")
  }

  @Test
  fun `does not search again once the search agrees with a removed override`() {
    respond(found)
    service(overrides = "film_id\timdb_id\ttitle\n8810\ttt50340469\tBeril_XXX\n").match(listOf(beril))
    service("2026-11-12T08:00:00Z").match(listOf(beril))
    wireMockServer.resetRequests()
    service("2026-11-12T08:30:00Z").match(listOf(beril))
    expect(wireMockServer.findAll(getRequestedFor(urlPathMatching("/suggestion/x/.*"))).size).toEqual(0)
  }

  @Test
  fun `skips a film without a year`() {
    respond(found)
    service().match(listOf(poffFilm(1, year = null)))
    expect(row(poffFilm(1))).toEqual(null)
  }
}
