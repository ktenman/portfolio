package ee.tenman.portfolio.service.poff

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.notFound
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.service.infrastructure.MinioService
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.wiremock.spring.InjectWireMock
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

@IntegrationTest
class PoffStillServiceIT {
  @Resource
  private lateinit var poffStillService: PoffStillService

  @Resource
  private lateinit var minioService: MinioService

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  private fun still(
    path: String = "/img/${UUID.randomUUID()}_big_16_9.jpeg",
    wide: Int = 1920,
  ): String {
    val image = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(wide, wide * 9 / 16, BufferedImage.TYPE_INT_RGB), "jpg", it) }
    wireMockServer.stubFor(get(path).willReturn(ok().withBody(image.toByteArray())))
    return wireMockServer.baseUrl() + path
  }

  private fun width(name: String): Int? = minioService.downloadPoffStill(name)?.let { ImageIO.read(ByteArrayInputStream(it)).width }

  @Test
  fun `stores each still at 640 and 1280 wide`() {
    val url = still()
    poffStillService.store(listOf(poffFilm(1, stills = listOf(url))))
    expect(listOf(width("${stillKey(url)}-640"), width("${stillKey(url)}-1280"))).toEqual(listOf(640, 1280))
  }

  @Test
  fun `downloads a still only once`() {
    val path = "/img/${UUID.randomUUID()}_big_16_9.jpeg"
    val films = listOf(poffFilm(1, stills = listOf(still(path))))
    poffStillService.store(films)
    poffStillService.store(films)
    wireMockServer.verify(1, getRequestedFor(urlPathEqualTo(path)))
  }

  @Test
  fun `stores the thumbnail of the trailer as its poster`() {
    val trailer = UUID.randomUUID().toString()
    still("/vi/$trailer/maxresdefault.jpg")
    poffStillService.store(listOf(poffFilm(1, trailer = trailer)))
    expect(width("${posterKey(trailer)}-1280")).toEqual(1280)
  }

  @Test
  fun `stores the first still as the poster when the trailer has no thumbnail`() {
    val trailer = UUID.randomUUID().toString()
    wireMockServer.stubFor(get("/vi/$trailer/maxresdefault.jpg").willReturn(notFound()))
    poffStillService.store(listOf(poffFilm(1, stills = listOf(still(wide = 800)), trailer = trailer)))
    expect(width("${posterKey(trailer)}-1280")).toEqual(800)
  }

  @Test
  fun `downloads a trailer poster only once`() {
    val trailer = UUID.randomUUID().toString()
    still("/vi/$trailer/maxresdefault.jpg")
    val films = listOf(poffFilm(1, trailer = trailer))
    poffStillService.store(films)
    poffStillService.store(films)
    wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/vi/$trailer/maxresdefault.jpg")))
  }

  @Test
  fun `keeps storing the other stills when one cannot be downloaded`() {
    val broken = "/img/${UUID.randomUUID()}_big_16_9.jpeg"
    wireMockServer.stubFor(get(broken).willReturn(serverError()))
    val url = still()
    poffStillService.store(listOf(poffFilm(1, stills = listOf(wireMockServer.baseUrl() + broken, url))))
    expect(minioService.downloadPoffStill("${stillKey(url)}-640")).notToEqualNull()
  }
}
