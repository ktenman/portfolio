package ee.tenman.portfolio.auto24

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.exception.CaptchaException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class Auto24ServiceTest {
  private val auto24ProxyClient: Auto24ProxyClient = mockk()
  private val captchaService: CaptchaService = mockk()
  private lateinit var auto24Service: Auto24Service

  @BeforeEach
  fun setUp() {
    auto24Service = Auto24Service(auto24ProxyClient, captchaService)
  }

  @Test
  fun `should return price directly when no captcha required`() {
    val regNr = "463BKH"
    val expectedPrice = "2600 € kuni 11500 €"
    every { auto24ProxyClient.getCaptcha(CaptchaRequest(regNr)) } returns
      CaptchaResponse(status = "success", price = expectedPrice)

    val result = auto24Service.findCarPrice(regNr)

    expect(result).toEqual(expectedPrice)
    verify(exactly = 1) { auto24ProxyClient.getCaptcha(CaptchaRequest(regNr)) }
  }

  @Test
  fun `should solve captcha and return price on first attempt`() {
    val regNr = "876BCH"
    val sessionId = UUID.randomUUID().toString()
    val captchaImage = "base64image"
    val solution = "ABC123"
    val expectedPrice = "5000 € kuni 8000 €"
    every { auto24ProxyClient.getCaptcha(CaptchaRequest(regNr)) } returns
      CaptchaResponse(sessionId = sessionId, captchaImage = captchaImage)
    every { captchaService.predict(any()) } returns
      PredictionResponse(UUID.randomUUID().toString(), solution, 0.99, 50.0)
    every { auto24ProxyClient.submitCaptcha(SubmitRequest(sessionId, solution)) } returns
      SubmitResponse(status = "success", price = expectedPrice)

    val result = auto24Service.findCarPrice(regNr)

    expect(result).toEqual(expectedPrice)
  }

  @Test
  fun `should retry when captcha solution is incorrect`() {
    val regNr = "999XXX"
    val sessionId = UUID.randomUUID().toString()
    val captchaImage = "base64image"
    val wrongSolution = "WRONG"
    val correctSolution = "RIGHT"
    val expectedPrice = "3000 € kuni 6000 €"
    every { auto24ProxyClient.getCaptcha(CaptchaRequest(regNr)) } returns
      CaptchaResponse(sessionId = sessionId, captchaImage = captchaImage)
    every { captchaService.predict(any()) } returnsMany
      listOf(
        PredictionResponse(UUID.randomUUID().toString(), wrongSolution, 0.95, 50.0),
        PredictionResponse(UUID.randomUUID().toString(), correctSolution, 0.99, 50.0),
      )
    every { auto24ProxyClient.submitCaptcha(any()) } returnsMany
      listOf(
        SubmitResponse(status = "captcha_failed", message = "Incorrect"),
        SubmitResponse(status = "success", price = expectedPrice),
      )

    val result = auto24Service.findCarPrice(regNr)

    expect(result).toEqual(expectedPrice)
    verify(exactly = 2) { auto24ProxyClient.getCaptcha(any()) }
    verify(exactly = 2) { auto24ProxyClient.submitCaptcha(any()) }
  }

  @Test
  fun `should throw exception when no session id returned`() {
    val regNr = "NOSESS"
    every { auto24ProxyClient.getCaptcha(CaptchaRequest(regNr)) } returns
      CaptchaResponse(captchaImage = "image")

    expect { auto24Service.findCarPrice(regNr) }
      .toThrow<CaptchaException>()
      .messageToContain("No session ID returned")
  }

  @Test
  fun `should throw exception when no captcha image returned`() {
    val regNr = "NOIMG"
    every { auto24ProxyClient.getCaptcha(CaptchaRequest(regNr)) } returns
      CaptchaResponse(sessionId = "session123")

    expect { auto24Service.findCarPrice(regNr) }
      .toThrow<CaptchaException>()
      .messageToContain("No CAPTCHA image returned")
  }

  @Test
  fun `should throw exception after max attempts exceeded`() {
    val regNr = "FAIL"
    val sessionId = UUID.randomUUID().toString()
    every { auto24ProxyClient.getCaptcha(CaptchaRequest(regNr)) } returns
      CaptchaResponse(sessionId = sessionId, captchaImage = "image")
    every { captchaService.predict(any()) } returns
      PredictionResponse(UUID.randomUUID().toString(), "WRONG", 0.99, 50.0)
    every { auto24ProxyClient.submitCaptcha(any()) } returns
      SubmitResponse(status = "captcha_failed", message = "Incorrect")

    expect { auto24Service.findCarPrice(regNr) }
      .toThrow<CaptchaException>()
      .messageToContain("Failed to solve CAPTCHA after 10 attempts")

    verify(exactly = 10) { auto24ProxyClient.getCaptcha(any()) }
  }
}
