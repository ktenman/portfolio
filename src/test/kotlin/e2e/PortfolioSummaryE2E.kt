package e2e

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Selenide.clearBrowserLocalStorage
import com.codeborne.selenide.Selenide.element
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
import org.openqa.selenium.By.cssSelector
import org.openqa.selenium.By.tagName
import org.openqa.selenium.TimeoutException
import java.time.Duration

private const val SUMMARY_BASE_URL = "http://localhost:61234/"

@ExtendWith(RetryExtension::class)
@Retry(times = 3, onExceptions = [ElementNotFound::class, TimeoutException::class])
class PortfolioSummaryE2E {
  @BeforeEach
  fun setUp() {
    BrowserConfig.configureBrowser()
    open(SUMMARY_BASE_URL)
  }

  @AfterEach
  fun tearDown() {
    clearBrowserLocalStorage()
  }

  @Test
  fun `should load summary tab with data table and no error alert`() {
    element(tagName("table")).shouldBe(visible, Duration.ofSeconds(10))
    val tableRows = elements(cssSelector("table tbody tr"))
    expect(tableRows.size()).toBeGreaterThan(0)
    expect(elements(className("alert-danger")).size()).toEqual(0)
  }

  @Test
  fun `should display xirr annual return column in summary table`() {
    element(tagName("table")).shouldBe(visible, Duration.ofSeconds(10))
    val headerCells = elements(cssSelector("table th"))
    expect(headerCells.size()).toBeGreaterThan(4)
    headerCells.findBy(text("XIRR Annual Return")).shouldBe(visible)
  }

  @Test
  fun `should display platform filter buttons after summary data loads`() {
    element(tagName("table")).shouldBe(visible, Duration.ofSeconds(10))
    val platformButtons = elements(className("platform-btn"))
    expect(platformButtons.size()).toBeGreaterThan(0)
  }
}
