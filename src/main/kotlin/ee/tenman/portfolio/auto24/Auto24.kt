package ee.tenman.portfolio.auto24

import com.codeborne.selenide.Condition
import com.codeborne.selenide.Configuration
import com.codeborne.selenide.Selenide
import org.openqa.selenium.By
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class Auto24(private val captchaService: CaptchaService) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val MAX_ATTEMPTS = 10
    private const val RETRY_DELAY_MS = 2000L
    private const val AUTO24_URL = "https://www.auto24.ee/ostuabi/?t=soiduki-turuhinna-paring&vpc_reg_nr=463bkh&checksec1=387c&vpc_reg_search=1"
  }

  fun findCarPrice(regNr: String): String {
    Configuration.browserSize = "1920x1080"
    Configuration.headless = true
    Configuration.browser = "firefox"

    try {
      openPageAndHandleCookies(regNr)
      solveCaptcha(regNr)
      return extractCarPrice()
    } catch (e: Exception) {
      log.error("Failed to find car price for $regNr", e)
      return "Error processing request"
    }
  }

  private fun openPageAndHandleCookies(regNr: String) {
    Selenide.open(AUTO24_URL)
    TimeUnit.SECONDS.sleep(2)

    val acceptCookies = Selenide.elements(By.tagName("button"))
      .find(Condition.text("Nõustun"))
    if (acceptCookies.exists()) {
      acceptCookies.click()
    }

    Selenide.element(By.name("vpc_reg_nr")).value = regNr
    Selenide.element(By.className("sbmt")).click()
    TimeUnit.SECONDS.sleep(2)
  }

  private fun solveCaptcha(regNr: String) {
    repeat(MAX_ATTEMPTS) { attempt ->
      log.info("CAPTCHA attempt ${attempt + 1} of $MAX_ATTEMPTS")

      if (processCaptchaAttempt()) return

      if (Selenide.element(".vpc_error_msg").exists()) {
        log.info("Error message found, retrying...")
        Selenide.element(By.name("vpc_reg_nr")).value = regNr
        Selenide.element(By.className("sbmt")).click()
        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS)
      }
    }
    throw RuntimeException("Failed to solve CAPTCHA after $MAX_ATTEMPTS attempts")
  }

  private fun processCaptchaAttempt(): Boolean {
    TimeUnit.SECONDS.sleep(2)
    val screenshot = Selenide.element(By.id("vpc_captcha")).screenshot() ?: return false

    val base64Image = Base64.getEncoder().encodeToString(screenshot.toPath().toFile().readBytes())
    val response = captchaService.predict(PredictionRequest(UUID.randomUUID(), base64Image))

    log.info("Captcha prediction: ${response.prediction}")
    log.info("Confidence: ${response.confidence}")
    log.info("Processing time: ${response.processingTimeMs}ms")

    Selenide.element(By.name("checksec1")).value = response.prediction
    Selenide.element(By.className("sbmt")).click()

    return !Selenide.element(By.id("vpc_captcha")).exists()
  }

  private fun extractCarPrice(): String {
    TimeUnit.SECONDS.sleep(1)
    return try {
      val priceElement = Selenide.element(By.className("result"))
        .findAll(By.className("label"))
        .last()
        .parent()
        .find(By.tagName("b"))

      if (priceElement.exists()) {
        priceElement.text
      } else {
        "Price not found"
      }
    } catch (e: Exception) {
      log.error("Failed to extract car price", e)
      "Error extracting price"
    }
  }
}
