package e2e

import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Selenide.clearBrowserLocalStorage
import com.codeborne.selenide.Selenide.elements
import com.codeborne.selenide.Selenide.open
import com.codeborne.selenide.ex.ElementNotFound
import e2e.retry.Retry
import e2e.retry.RetryExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By.className
import org.openqa.selenium.TimeoutException
import org.slf4j.LoggerFactory

private const val LIGHTYEAR_BASE_URL = "https://lightyear.com/en/etf/VUAA:XETRA/holdings/1"

@ExtendWith(RetryExtension::class)
@Retry(times = 3, onExceptions = [ElementNotFound::class, TimeoutException::class])
class LightyearE2ETests {
  private companion object {
    private val log = LoggerFactory.getLogger(LightyearE2ETests::class.java)
  }

  @BeforeEach
  fun setUp() {
    BrowserConfig.configureBrowser()
    open(LIGHTYEAR_BASE_URL)
  }

  @AfterEach
  fun tearDown() {
    clearBrowserLocalStorage()
  }

  @Test
  fun `test qdve`() {
    log.info("Starting qdve")
    val text =
      elements(className("table-row"))
      .first { it.text.contains("%") }
      .text()

    log.info("Found text: $text")
  }
}
