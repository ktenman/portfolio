package e2e

import com.codeborne.selenide.Configuration
import com.codeborne.selenide.Selenide.clearBrowserLocalStorage
import com.codeborne.selenide.Selenide.executeJavaScript
import com.codeborne.selenide.Selenide.open
import com.codeborne.selenide.Selenide.sleep
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.chrome.ChromeOptions
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Base64

class LightyearApiMonitorTest {
  private companion object {
    private val log = LoggerFactory.getLogger(LightyearApiMonitorTest::class.java)
    private val mapper = jacksonObjectMapper()
  }

  @BeforeEach
  fun setUp() {
    val chromeOptions = ChromeOptions()
    Configuration.browser = "chrome"
    Configuration.browserSize = "1920x1080"
    Configuration.timeout = 10000
    Configuration.headless = false
    Configuration.browserCapabilities = chromeOptions
  }

  @AfterEach
  fun tearDown() {
    clearBrowserLocalStorage()
  }

  @Test
  fun `should extract instrument ID and capture price history from Lightyear XAIX`() {
    log.info("Opening Lightyear XAIX page...")
    open("https://lightyear.com/en/etf/XAIX:XETRA")

    sleep(8000)

    log.info("Extracting instrument ID from page...")

    val instrumentIdScript =
      """
      const scripts = Array.from(document.getElementsByTagName('script'));
      for (const script of scripts) {
        const text = script.textContent || '';
        const match = text.match(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i);
        if (match) {
          return match[0];
        }
      }
      const html = document.documentElement.outerHTML;
      const match = html.match(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i);
      return match ? match[0] : null;
      """.trimIndent()

    val instrumentId = executeJavaScript<String>(instrumentIdScript)

    if (instrumentId != null) {
      log.info("Found instrument ID: $instrumentId")

      val ranges = listOf("1d", "1w", "1m", "3m", "6m", "1y", "5y", "max")

      ranges.forEach { range ->
        log.info("========================================")
        log.info("Fetching range: $range")
        log.info("========================================")

        val path = "/v1/market-data/$instrumentId/chart?range=$range"
        val encodedPath = Base64.getEncoder().encodeToString(path.toByteArray()).replace("=", "")
        val url = "https://lightyear.com/fetch?path=$encodedPath&withAPIKey=true"

        val fetchScript =
          """
          return fetch('$url')
            .then(response => response.text())
            .then(text => ({ success: true, body: text }))
            .catch(error => ({ success: false, error: error.message }));
          """.trimIndent()

        val result = executeJavaScript<Map<String, Any>>(fetchScript)
        val success = result?.get("success") as? Boolean ?: false
        val body = result?.get("body") as? String

        if (success && body != null) {
          log.info("Success! Response length: ${body.length} characters")

          val outputFile = File("/tmp/lightyear-chart-$range.json")
          outputFile.writeText(body)
          log.info("Saved to: ${outputFile.absolutePath}")

          try {
            val jsonNode = mapper.readTree(body)
            val dataPoints = jsonNode.get("data")?.size() ?: 0
            log.info("Data points: $dataPoints")

            if (dataPoints > 0) {
              val firstPoint = jsonNode.get("data")?.get(0)
              val lastPoint = jsonNode.get("data")?.get(dataPoints - 1)
              log.info("First: $firstPoint")
              log.info("Last: $lastPoint")
            } else {
              log.info("Response preview: ${body.take(200)}")
            }
          } catch (e: Exception) {
            log.warn("Could not parse: ${e.message}")
            log.info("Response preview: ${body.take(300)}")
          }
        } else {
          log.error("Failed to fetch $range")
        }

        sleep(1000)
      }
    } else {
      log.error("Could not find instrument ID in page")
    }

    log.info("")
    log.info("========================================")
    log.info("COMPLETE")
    log.info("========================================")
    log.info("Check /tmp/lightyear-chart-*.json files")
  }
}
