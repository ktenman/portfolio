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

private const val INSTRUMENTS_BASE_URL = "http://localhost:61234/instruments"

@ExtendWith(RetryExtension::class)
@Retry(times = 3, onExceptions = [ElementNotFound::class, TimeoutException::class])
class InstrumentManagementE2E {
  @BeforeEach
  fun setUp() {
    BrowserConfig.configureBrowser()
    open(INSTRUMENTS_BASE_URL)
  }

  @AfterEach
  fun tearDown() {
    clearBrowserLocalStorage()
  }

  @Test
  fun `should display instruments page with title and navigation`() {
    val heading = element(tagName("h2")).shouldBe(visible, Duration.ofSeconds(10))
    heading.shouldHave(text("Instruments"))

    val navLinks = elements(cssSelector("nav a"))
    expect(navLinks.size()).toBeGreaterThan(3)

    val instrumentsLink = navLinks.findBy(text("Instruments"))
    expect(instrumentsLink.isDisplayed).toEqual(true)
  }

  @Test
  fun `should display platform filter buttons`() {
    val platformButtons = elements(className("platform-btn"))
    platformButtons.first().shouldBe(visible, Duration.ofSeconds(10))
    expect(platformButtons.size()).toBeGreaterThan(0)

    val clearAllButton = platformButtons.findBy(text("Clear All"))
    expect(clearAllButton.isDisplayed).toEqual(true)
  }

  @Test
  fun `should toggle platform filter when clicking button`() {
    val platformButtons = elements(className("platform-btn"))
    platformButtons.first().shouldBe(visible, Duration.ofSeconds(10))

    val firstPlatformButton = platformButtons.first()
    val initialActiveState = firstPlatformButton.getAttribute("class")?.contains("active") ?: false

    firstPlatformButton.click()
    Thread.sleep(300)

    val newActiveState = firstPlatformButton.getAttribute("class")?.contains("active") ?: false
    expect(newActiveState).toEqual(!initialActiveState)
  }

  @Test
  fun `should display period selector with options`() {
    val periodSelector = element(className("period-select")).shouldBe(visible, Duration.ofSeconds(10))
    expect(periodSelector.isDisplayed).toEqual(true)

    val options = periodSelector.findAll(tagName("option"))
    expect(options.size()).toBeGreaterThan(3)

    val optionTexts = options.texts()
    expect(optionTexts.contains("24H")).toEqual(true)
    expect(optionTexts.contains("7D")).toEqual(true)
    expect(optionTexts.contains("30D")).toEqual(true)
  }

  @Test
  fun `should display active only toggle switch`() {
    val toggleContainer = element(className("toggle-container")).shouldBe(visible, Duration.ofSeconds(10))
    expect(toggleContainer.isDisplayed).toEqual(true)

    val toggleLabel = element(className("toggle-label"))
    toggleLabel.shouldHave(text("Active only"))

    val toggleSwitch = element(className("toggle-switch"))
    expect(toggleSwitch.isDisplayed).toEqual(true)
  }

  @Test
  fun `should display instruments table with data`() {
    element(tagName("table")).shouldBe(visible, Duration.ofSeconds(10))

    val tableRows = elements(cssSelector("table tbody tr"))
    expect(tableRows.size()).toBeGreaterThan(0)

    val headerCells = elements(cssSelector("table th"))
    expect(headerCells.size()).toBeGreaterThan(5)
  }

  @Test
  fun `should display instrument details in table rows`() {
    val tableRows = elements(cssSelector("table tbody tr"))
    tableRows.first().shouldBe(visible, Duration.ofSeconds(10))
    expect(tableRows.size()).toBeGreaterThan(0)

    val firstRowCells = tableRows.first().findAll(tagName("td"))
    expect(firstRowCells.size()).toBeGreaterThan(5)

    val cellTexts = firstRowCells.texts()
    val hasPrice = cellTexts.any { it.contains("€") }
    expect(hasPrice).toEqual(true)
  }

  @Test
  fun `should toggle active only switch and update table`() {
    val tableRows = elements(cssSelector("table tbody tr"))
    tableRows.first().shouldBe(visible, Duration.ofSeconds(10))

    val toggleSwitch = element(className("toggle-switch"))
    toggleSwitch.click()
    Thread.sleep(500)

    val updatedRows = elements(cssSelector("table tbody tr"))
    expect(updatedRows.size()).toBeGreaterThan(0)
  }

  @Test
  fun `should clear all platform filters when clicking clear all button`() {
    val platformButtons = elements(className("platform-btn"))
    platformButtons.first().shouldBe(visible, Duration.ofSeconds(10))

    val clearAllButton = platformButtons.findBy(text("Clear All"))
    clearAllButton.click()
    Thread.sleep(500)

    val selectAllButton = elements(className("platform-btn")).findBy(text("Select All"))
    expect(selectAllButton.isDisplayed).toEqual(true)
  }
}
