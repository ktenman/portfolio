package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.PoffCatalogue
import ee.tenman.portfolio.poff.PoffClient
import ee.tenman.portfolio.repository.PoffImdbRepository
import ee.tenman.portfolio.repository.PoffSnapshotRepository
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.poff.ImdbMatchService
import ee.tenman.portfolio.service.poff.ImdbRatingService
import ee.tenman.portfolio.service.poff.PoffCatalogueService
import ee.tenman.portfolio.service.poff.PoffImportService
import ee.tenman.portfolio.service.poff.poffFilm
import ee.tenman.portfolio.service.poff.poffScreening
import ee.tenman.portfolio.service.poff.ratingsFile
import ee.tenman.portfolio.service.poff.tallinnClock
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.wiremock.spring.InjectWireMock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@IntegrationTest
class PoffImportJobIT {
  @Resource
  private lateinit var poffImportService: PoffImportService

  @Resource
  private lateinit var poffSnapshotRepository: PoffSnapshotRepository

  @Resource
  private lateinit var jobExecutionService: JobExecutionService

  @Resource
  private lateinit var imdbMatchService: ImdbMatchService

  @Resource
  private lateinit var poffImdbRepository: PoffImdbRepository

  @Resource
  private lateinit var imdbRatingService: ImdbRatingService

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  private fun catalogueService(at: String = "2026-11-10T08:00:00Z") =
    PoffCatalogueService(poffSnapshotRepository, poffImdbRepository, tallinnClock(at))

  private fun job(
    at: String = "2026-11-10T08:00:00Z",
    importService: PoffImportService = poffImportService,
    ratingService: ImdbRatingService = imdbRatingService,
  ) = PoffImportJob(importService, catalogueService(at), imdbMatchService, ratingService, jobExecutionService, mockk(), tallinnClock(at))

  private fun afterFailure(
    later: String,
    between: () -> Unit = {},
  ): PoffImportJob {
    val clock = MutableClock(Instant.parse("2026-12-01T08:00:00Z"))
    val catalogueService = PoffCatalogueService(poffSnapshotRepository, poffImdbRepository, clock)
    val job = PoffImportJob(poffImportService, catalogueService, imdbMatchService, imdbRatingService, jobExecutionService, mockk(), clock)
    respond("/en/films/", "Bad gateway", 502)
    runCatching { job.execute() }
    wireMockServer.resetRequests()
    between()
    clock.instant = Instant.parse(later)
    runCatching { job.execute() }
    return job
  }

  private fun crawlsAfterFailure(later: String): Int {
    afterFailure(later)
    return wireMockServer.findAll(getRequestedFor(urlPathEqualTo("/en/films/"))).size
  }

  private fun failed(job: PoffImportJob) =
    SimpleMeterRegistry()
    .also(job::bindTo)
    .get("portfolio.poff.import.failed")
    .gauge()
    .value()

  private fun respond(
    path: String,
    body: String,
    status: Int = 200,
  ) {
    wireMockServer.stubFor(
      get(
        urlPathEqualTo(path),
      ).willReturn(aResponse().withStatus(status).withHeader("Content-Type", "text/html").withBody(body.toByteArray())),
    )
  }

  private fun page(
    title: String,
    screening: Long,
  ) = """
    <section class="f_title_box"><h1 class="film_title_detailed">$title</h1>
    <div class="str_info_grid_3"><div class="str_info_label">Year</div><div class="str_info_value">2026</div></div></section>
    <section class="f_screenings_box"><div class="card card_screening" id="screening_$screening" data-ends-at="1794690720000">
    <span class="screening_hall">9th Hall </span><span class="screening_venue">Apollo Kino Plaza</span>
    <button class="cal_add_inline" data-cal-uid="screening-$screening@poff.ee" data-cal-start="2026-11-14T19:15:00.000Z" data-cal-location="9th Hall, Apollo Kino Plaza, Tallinn"></button>
    </div></section>
    """

  private fun suggestion() {
    wireMockServer.stubFor(
      get(urlPathMatching("/suggestion/x/.*"))
        .willReturn(okJson("""{"d":[{"id":"tt50340469","l":"Beril_XXX","qid":"movie","y":2026}]}""")),
    )
  }

  private fun festival() {
    respond(
      "/en/films/",
      """<a class="card_film" id="8810" href="/en/film/beril_xxx"></a><a class="card_film" id="8549" href="/en/film/a-girl-unknown"></a>""",
    )
    respond("/en/film/beril_xxx/", page("Beril_XXX", 6688))
  }

  private fun ended(vararg ids: Long) =
    catalogueService("2026-11-23T08:00:00Z").store(
      PoffCatalogue(ids.map { poffFilm(it, screenings = listOf(poffScreening(it, "2026-11-22T17:00:00Z"))) }),
    )

  @Test
  fun `imports every film of the listing with its screenings`() {
    festival()
    respond("/en/film/a-girl-unknown/", page("Tüdruk ilma nimeta", 6701))
    job().execute()
    expect(
      poffSnapshotRepository
      .findTopByOrderByIdDesc()
      ?.catalogue
      ?.films
      ?.map { it.englishTitle to it.screenings.single().id },
        ).toEqual(listOf("Beril_XXX" to 6688L, "Tüdruk ilma nimeta" to 6701L))
  }

  @Test
  fun `reads the rating of a film it newly matched`() {
    festival()
    respond("/en/film/a-girl-unknown/", page("Tüdruk ilma nimeta", 6701))
    suggestion()
    wireMockServer.stubFor(
      get(urlPathEqualTo("/title.ratings.tsv.gz")).willReturn(aResponse().withBody(ratingsFile("tt50340469\t6.9\t87"))),
    )
    job().execute()
    expect(poffImdbRepository.findAll().first { it.filmId == 8810L }.votes).toEqual(87)
  }

  @Test
  fun `reads the ratings on the next run when their download failed`() {
    festival()
    respond("/en/film/a-girl-unknown/", page("Tüdruk ilma nimeta", 6701))
    suggestion()
    wireMockServer.stubFor(get(urlPathEqualTo("/title.ratings.tsv.gz")).willReturn(aResponse().withStatus(503)))
    val job = job()
    runCatching { job.execute() }
    wireMockServer.stubFor(
      get(urlPathEqualTo("/title.ratings.tsv.gz")).willReturn(aResponse().withBody(ratingsFile("tt50340469\t6.9\t87"))),
    )
    job.execute()
    expect(poffImdbRepository.findAll().first { it.filmId == 8810L }.votes).toEqual(87)
  }

  @Test
  fun `fails the run when the ratings of a new match cannot be downloaded`() {
    festival()
    respond("/en/film/a-girl-unknown/", page("Tüdruk ilma nimeta", 6701))
    suggestion()
    wireMockServer.stubFor(get(urlPathEqualTo("/title.ratings.tsv.gz")).willReturn(aResponse().withStatus(503)))
    expect { job().runScheduledJob() }.toThrow<IllegalStateException>()
  }

  @Test
  fun `keeps the stored catalogue when a film page fails`() {
    catalogueService().store(PoffCatalogue(listOf(poffFilm(1), poffFilm(2))))
    festival()
    respond("/en/film/a-girl-unknown/", "Bad gateway", 502)
    expect { job().execute() }.toThrow<Exception>()
    expect(
      poffSnapshotRepository
      .findTopByOrderByIdDesc()
      ?.catalogue
      ?.films
      ?.map { it.id },
        ).toEqual(listOf(1L, 2L))
  }

  @Test
  fun `names the film page that cannot be read`() {
    festival()
    respond("/en/film/a-girl-unknown/", page("Tüdruk ilma nimeta", 6701).replace("data-cal-start", "data-cal-begin"))
    expect { job().execute() }.toThrow<IllegalStateException>().messageToContain("a-girl-unknown")
  }

  @Test
  fun `crawls once when a film page fails`() {
    wireMockServer.resetRequests()
    festival()
    respond("/en/film/a-girl-unknown/", "Bad gateway", 502)
    runCatching { job().runScheduledJob() }
    expect(wireMockServer.findAll(getRequestedFor(urlPathEqualTo("/en/films/"))).size).toEqual(1)
  }

  @Test
  fun `does not import again within an hour of a failed first import`() {
    expect(crawlsAfterFailure("2026-12-01T08:30:00Z")).toEqual(0)
  }

  @Test
  fun `imports again an hour after a failed first import`() {
    expect(crawlsAfterFailure("2026-12-01T09:01:00Z")).toEqual(1)
  }

  @Test
  fun `keeps reporting a failed import through runs that import nothing`() {
    expect(failed(afterFailure("2026-12-01T08:30:00Z"))).toEqual(1.0)
  }

  @Test
  fun `clears a reported failure once an import succeeds`() {
    val job =
      afterFailure("2026-12-01T09:01:00Z") {
        festival()
        respond("/en/film/a-girl-unknown/", page("Tüdruk ilma nimeta", 6701))
      }
    expect(failed(job)).toEqual(0.0)
  }

  @Test
  fun `keeps reporting a failure while the ratings download keeps failing`() {
    festival()
    respond("/en/film/a-girl-unknown/", page("Tüdruk ilma nimeta", 6701))
    suggestion()
    val seen = mutableListOf<Double>()
    val ratings = mockk<ImdbRatingService>()
    val job = job("2026-11-14T08:00:00Z", ratingService = ratings)
    every { ratings.refresh() } answers {
      seen += failed(job)
      throw IllegalStateException("Ratings download timed out")
    }
    repeat(2) { runCatching { job.execute() } }
    expect(seen.last()).toEqual(1.0)
  }

  @Test
  fun `does not import when the catalogue is not due`() {
    wireMockServer.resetRequests()
    catalogueService(
      "2026-11-23T08:00:00Z",
    ).store(PoffCatalogue(listOf(poffFilm(1, screenings = listOf(poffScreening(1, "2026-11-10T16:00:00Z"))))))
    festival()
    job("2026-11-23T09:00:00Z").execute()
    expect(wireMockServer.findAll(getRequestedFor(urlPathEqualTo("/en/films/"))).size).toEqual(0)
  }

  @Test
  fun `reads no film page after the festival when the listing has no new film`() {
    ended(8810, 8549)
    festival()
    respond("/en/film/a-girl-unknown/", page("Tüdruk ilma nimeta", 6701))
    wireMockServer.resetRequests()
    job("2026-11-25T08:00:00Z").execute()
    expect(wireMockServer.findAll(getRequestedFor(urlPathMatching("/en/film/.*"))).size).toEqual(0)
  }

  @Test
  fun `imports again after the festival when the listing has a new film`() {
    ended(8810)
    festival()
    respond("/en/film/a-girl-unknown/", page("Tüdruk ilma nimeta", 6701))
    job("2026-11-25T08:00:00Z").execute()
    expect(
      poffSnapshotRepository
      .findTopByOrderByIdDesc()
      ?.catalogue
      ?.films
      ?.map { it.id },
        ).toEqual(listOf(8810L, 8549L))
  }

  @Test
  fun `does not start a second import while one is running`() {
    catalogueService().store(PoffCatalogue(listOf(poffFilm(1))))
    val poff = SlowPoff()
    val job = job(importService = PoffImportService(poff, Duration.ZERO))
    val first = thread { runCatching { job.execute() } }
    poff.started.await(10, TimeUnit.SECONDS)
    runCatching { job.execute() }
    poff.finish.countDown()
    first.join(10_000)
    expect(poff.crawls.get()).toEqual(1)
  }

  private class SlowPoff : PoffClient {
    val started = CountDownLatch(1)
    val finish = CountDownLatch(1)
    val crawls = AtomicInteger()

    override fun films(): String {
      if (crawls.incrementAndGet() > 1) return ""
      started.countDown()
      finish.await(10, TimeUnit.SECONDS)
      return ""
    }

    override fun film(slug: String): String = ""
  }

  private class MutableClock(
    var instant: Instant,
  ) : Clock() {
    override fun instant(): Instant = instant

    override fun withZone(zone: ZoneId?): Clock = this

    override fun getZone(): ZoneId = ZoneId.of("Europe/Tallinn")
  }
}
