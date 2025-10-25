package e2e

import com.codeborne.selenide.Selenide.clearBrowserLocalStorage
import com.codeborne.selenide.Selenide.open
import com.codeborne.selenide.Selenide.screenshot
import e2e.retry.Retry
import e2e.retry.RetryExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
import org.slf4j.LoggerFactory

@ExtendWith(RetryExtension::class)
@Retry(times = 3)
class LightyearLogoExplorationTest {
  private companion object {
    private val log = LoggerFactory.getLogger(LightyearLogoExplorationTest::class.java)
  }

  @BeforeEach
  fun setUp() {
    BrowserConfig.configureBrowser()
  }

  @AfterEach
  fun tearDown() {
    clearBrowserLocalStorage()
  }

  @Test
  fun `explore Lightyear stock page for logo - AAPL`() {
    val ticker = "AAPL"
    val url = "https://lightyear.com/en/stock/$ticker"

    log.info("Exploring Lightyear page for ticker: $ticker")
    log.info("URL: $url")

    open(url)
    Thread.sleep(3000)

    screenshot("lightyear-$ticker-page")

    val imageElements = com.codeborne.selenide.Selenide.`$`(By.tagName("body"))
      .findAll(By.tagName("img"))

    log.info("Found ${imageElements.size} image elements")

    imageElements.forEachIndexed { index, element ->
      val src = element.getAttribute("src")
      val alt = element.getAttribute("alt")
      val className = element.getAttribute("class")
      log.info("Image $index: src=$src, alt=$alt, class=$className")
    }
  }

  @Test
  @Disabled("Exploration test - enable manually to explore logo extraction")
  fun `explore Lightyear stock page for logo - TSLA`() {
    val ticker = "TSLA"
    val url = "https://lightyear.com/en/stock/$ticker"

    log.info("Exploring Lightyear page for ticker: $ticker")
    log.info("URL: $url")

    open(url)
    Thread.sleep(3000)

    screenshot("lightyear-$ticker-page")

    val imageElements = com.codeborne.selenide.Selenide.`$`(By.tagName("body"))
      .findAll(By.tagName("img"))

    log.info("Found ${imageElements.size} image elements")

    imageElements.forEachIndexed { index, element ->
      val src = element.getAttribute("src")
      val alt = element.getAttribute("alt")
      val className = element.getAttribute("class")
      log.info("Image $index: src=$src, alt=$alt, class=$className")
    }
  }

  @Test
  @Disabled("Exploration test - enable manually to explore logo extraction")
  fun `test direct logo URL pattern`() {
    val testCases = listOf(
      "AAPL" to "https://assets.lightyear.com/logos/AAPL.png",
      "TSLA" to "https://assets.lightyear.com/logos/TSLA.png",
      "GOOGL" to "https://assets.lightyear.com/logos/GOOGL.png"
    )

    testCases.forEach { (ticker, logoUrl) ->
      log.info("Testing direct logo URL for $ticker: $logoUrl")
      open(logoUrl)
      Thread.sleep(2000)
      screenshot("logo-direct-$ticker")
    }
  }
}
