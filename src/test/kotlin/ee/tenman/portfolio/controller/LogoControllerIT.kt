package ee.tenman.portfolio.controller

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import jakarta.annotation.Resource
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet

@IntegrationTest
class LogoControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var etfHoldingRepository: EtfHoldingRepository

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @BeforeEach
  fun setup() {
    stubFor(
      get(urlPathEqualTo("/user-by-session"))
        .withQueryParam("sessionId", equalTo(SESSION))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("user-details-response.json"),
        ),
    )
  }

  @Test
  fun `should serve an uploaded image resized as the logo of an ETF holding`() {
    val holding = etfHoldingRepository.save(EtfHolding(name = "Šveitsi Pank AG ${UUID.randomUUID()}"))
    mockMvc
      .perform(multipart("/api/logos/${holding.uuid}").file(png(64)).cookie(Cookie("AUTHSESSION", SESSION)))
      .andExpect(status().isOk)
    val logo =
      mockMvc
        .perform(mvcGet("/api/logos/${holding.uuid}").cookie(Cookie("AUTHSESSION", SESSION)))
        .andReturn()
        .response.contentAsByteArray
    expect(ImageIO.read(ByteArrayInputStream(logo)).width).toEqual(256)
  }

  @Test
  fun `should bump the logo version of an instrument when an image is uploaded`() {
    val instrument =
      instrumentRepository.save(
        Instrument(
          symbol = "EE${UUID.randomUUID().toString().take(10)}",
          name = "Tuleva Maailma Aktsiate Pensionifond õ",
          category = "ETF",
          baseCurrency = "EUR",
        ),
      )
    mockMvc
      .perform(multipart("/api/logos/${instrument.uuid}").file(png(300)).cookie(Cookie("AUTHSESSION", SESSION)))
      .andExpect(status().isOk)
    expect(instrumentRepository.findByUuid(instrument.uuid)?.logoUpdatedAt).notToEqualNull()
  }

  @Test
  fun `should reject an uploaded file that is not an image`() {
    val holding = etfHoldingRepository.save(EtfHolding(name = "Ärikinnisvara AS ${UUID.randomUUID()}"))
    val text = MockMultipartFile("file", "logo.png", "image/png", "pole pilt õ".toByteArray())
    mockMvc
      .perform(multipart("/api/logos/${holding.uuid}").file(text).cookie(Cookie("AUTHSESSION", SESSION)))
      .andExpect(status().isBadRequest)
  }

  private fun png(size: Int): MockMultipartFile {
    val output = ByteArrayOutputStream()
    ImageIO.write(BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB), "png", output)
    return MockMultipartFile("file", "logo.png", "image/png", output.toByteArray())
  }

  companion object {
    private const val SESSION = "NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz"
  }
}
