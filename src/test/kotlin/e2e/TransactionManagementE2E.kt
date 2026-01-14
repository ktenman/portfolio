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
import org.openqa.selenium.By.id
import org.openqa.selenium.By.tagName
import org.openqa.selenium.TimeoutException
import java.time.Duration

private const val TRANSACTIONS_BASE_URL = "http://localhost:61234/transactions"

@ExtendWith(RetryExtension::class)
@Retry(times = 3, onExceptions = [ElementNotFound::class, TimeoutException::class])
class TransactionManagementE2E {
  @BeforeEach
  fun setUp() {
    BrowserConfig.configureBrowser()
    open(TRANSACTIONS_BASE_URL)
  }

  @AfterEach
  fun tearDown() {
    clearBrowserLocalStorage()
  }

  @Test
  fun `should display transactions page with title and navigation`() {
    val heading = element(tagName("h2")).shouldBe(visible, Duration.ofSeconds(10))
    heading.shouldHave(text("Transactions"))

    val navLinks = elements(cssSelector("nav a"))
    expect(navLinks.size()).toBeGreaterThan(3)

    val transactionsLink = navLinks.findBy(text("Transactions"))
    expect(transactionsLink.isDisplayed).toEqual(true)
  }

  @Test
  fun `should display date filter inputs`() {
    val fromDateInput = element(id("fromDate")).shouldBe(visible, Duration.ofSeconds(10))
    expect(fromDateInput.isDisplayed).toEqual(true)

    val untilDateInput = element(id("untilDate"))
    expect(untilDateInput.isDisplayed).toEqual(true)

    val fromLabel = element(cssSelector("label[for='fromDate']"))
    fromLabel.shouldHave(text("From"))

    val untilLabel = element(cssSelector("label[for='untilDate']"))
    untilLabel.shouldHave(text("Until"))
  }

  @Test
  fun `should display quick dates dropdown`() {
    val quickDatesButton = elements(className("platform-btn")).findBy(text("Quick Dates"))
    quickDatesButton.shouldBe(visible, Duration.ofSeconds(10))
    expect(quickDatesButton.isDisplayed).toEqual(true)

    quickDatesButton.click()
    Thread.sleep(300)

    val dropdownMenu = element(className("dropdown-menu")).shouldBe(visible)
    val dropdownItems = dropdownMenu.findAll(className("dropdown-item"))
    expect(dropdownItems.size()).toBeGreaterThan(0)
  }

  @Test
  fun `should display platform filter buttons after data loads`() {
    element(tagName("table")).shouldBe(visible, Duration.ofSeconds(10))
    Thread.sleep(500)

    val platformButtons = elements(className("platform-btn"))
    expect(platformButtons.size()).toBeGreaterThan(0)
  }

  @Test
  fun `should toggle platform filter when clicking button`() {
    element(tagName("table")).shouldBe(visible, Duration.ofSeconds(10))
    Thread.sleep(500)

    val platformButtons = elements(className("platform-btn"))
    val lightyearButton = platformButtons.findBy(text("Lightyear"))
    val initialActiveState = lightyearButton.getAttribute("class")?.contains("active") ?: false

    lightyearButton.click()
    Thread.sleep(500)

    val newActiveState = lightyearButton.getAttribute("class")?.contains("active") ?: false
    expect(newActiveState).toEqual(!initialActiveState)
  }

  @Test
  fun `should display stats cards with profit information`() {
    val statsContainer = element(className("stats-container")).shouldBe(visible, Duration.ofSeconds(10))
    expect(statsContainer.isDisplayed).toEqual(true)

    val statCards = statsContainer.findAll(className("stat-card"))
    expect(statCards.size()).toBeGreaterThan(2)
  }

  @Test
  fun `should display transactions table with data`() {
    element(tagName("table")).shouldBe(visible, Duration.ofSeconds(10))

    val tableRows = elements(cssSelector("table tbody tr"))
    expect(tableRows.size()).toBeGreaterThan(0)

    val headerCells = elements(cssSelector("table th"))
    expect(headerCells.size()).toBeGreaterThan(4)
  }

  @Test
  fun `should display transaction details in table rows`() {
    val tableRows = elements(cssSelector("table tbody tr"))
    tableRows.first().shouldBe(visible, Duration.ofSeconds(10))
    expect(tableRows.size()).toBeGreaterThan(0)

    val firstRowCells = tableRows.first().findAll(tagName("td"))
    expect(firstRowCells.size()).toBeGreaterThan(4)

    val cellTexts = firstRowCells.texts()
    val hasPrice = cellTexts.any { it.contains("€") }
    expect(hasPrice).toEqual(true)
  }

  @Test
  fun `should activate platform filter when clicking platform button`() {
    element(tagName("table")).shouldBe(visible, Duration.ofSeconds(10))
    Thread.sleep(500)

    val platformButtons = elements(className("platform-btn"))
    val binanceButton = platformButtons.findBy(text("Binance"))
    binanceButton.click()
    Thread.sleep(500)

    val binanceActive = binanceButton.getAttribute("class")?.contains("active") ?: false
    expect(binanceActive).toEqual(true)
  }

  @Test
  fun `should toggle between select all and clear all buttons`() {
    element(tagName("table")).shouldBe(visible, Duration.ofSeconds(10))
    Thread.sleep(500)

    val selectAllButton = elements(className("platform-btn")).findBy(text("Select All"))
    selectAllButton.shouldBe(visible).click()
    Thread.sleep(500)

    val clearAllButton = elements(className("platform-btn")).findBy(text("Clear All"))
    expect(clearAllButton.isDisplayed).toEqual(true)

    clearAllButton.click()
    Thread.sleep(500)

    val selectAllButtonAgain = elements(className("platform-btn")).findBy(text("Select All"))
    expect(selectAllButtonAgain.isDisplayed).toEqual(true)
  }
}
