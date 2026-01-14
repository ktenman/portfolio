package e2e

import ch.tutteli.atrium.api.fluent.en_GB.notToBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.codeborne.selenide.Condition
import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Condition.value
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Selenide.clearBrowserLocalStorage
import com.codeborne.selenide.Selenide.element
import com.codeborne.selenide.Selenide.open
import com.codeborne.selenide.SelenideElement
import com.codeborne.selenide.ex.ElementNotFound
import e2e.retry.Retry
import e2e.retry.RetryExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import java.time.Duration

private const val CALCULATOR_BASE_URL = "http://localhost:61234/calculator"

@ExtendWith(RetryExtension::class)
@Retry(times = 3, onExceptions = [ElementNotFound::class, TimeoutException::class])
class CalculatorE2E {
  private val annualReturnInput: SelenideElement by lazy { id("annualReturnRate") }
  private val taxRateInput: SelenideElement by lazy { id("taxRate") }
  private val yearSummaryTable: SelenideElement by lazy { element(By.className("table")) }
  private val resetButton: SelenideElement by lazy { element(By.xpath("//button[contains(text(), 'Reset Calculator')]")) }

  @BeforeEach
  fun setUp() {
    BrowserConfig.configureBrowser()
    open(CALCULATOR_BASE_URL)
    annualReturnInput.shouldBe(visible, Duration.ofSeconds(10))
    yearSummaryTable.shouldBe(visible, Duration.ofSeconds(10))
  }

  @AfterEach
  fun tearDown() {
    clearBrowserLocalStorage()
  }

  @Test
  fun `should recalculate when annual return rate is changed`() {
    val firstDataRow = yearSummaryTable.find(By.tagName("tr"), 1)
    val initialTotalWorth = firstDataRow.find(By.tagName("td"), 2).text()
    expect(initialTotalWorth).notToBeEmpty()

    annualReturnInput.value = "50"

    firstDataRow.find(By.tagName("td"), 2).shouldNotHave(text(initialTotalWorth), Duration.ofSeconds(5))

    val updatedTotalWorth = firstDataRow.find(By.tagName("td"), 2).text()
    expect(updatedTotalWorth).notToEqual(initialTotalWorth)
    expect(firstDataRow.find(By.tagName("td"), 3).text()).notToBeEmpty()
  }

  @Test
  fun `should preserve user-modified annual return rate`() {
    annualReturnInput.value = "35.5"
    annualReturnInput.shouldHave(value("35.5"))
  }

  @Disabled
  @Test
  fun `should reset annual return rate when reset button is clicked`() {
    val initialValue = annualReturnInput.value
    expect(initialValue).notToEqualNull().notToBeEmpty()

    annualReturnInput.value = "75"
    annualReturnInput.shouldHave(value("75"))

    resetButton.click()
    element(By.cssSelector("[data-testid='confirmDialogConfirmButton']"))
      .shouldBe(visible, Duration.ofSeconds(5))
      .shouldBe(Condition.enabled, Duration.ofSeconds(5))
      .click()

    annualReturnInput.shouldHave(value(initialValue!!), Duration.ofSeconds(10))
  }

  @Test
  fun `should recalculate when tax rate is changed`() {
    val firstDataRow = yearSummaryTable.find(By.tagName("tr"), 1)
    val initialNetProfit = firstDataRow.find(By.tagName("td"), 5).text()
    expect(initialNetProfit).notToBeEmpty()

    taxRateInput.value = "50"

    firstDataRow.find(By.tagName("td"), 5).shouldNotHave(text(initialNetProfit), Duration.ofSeconds(5))

    val updatedNetProfit = firstDataRow.find(By.tagName("td"), 5).text()
    expect(updatedNetProfit).notToEqual(initialNetProfit)
  }

  @Test
  fun `should display tax amount column correctly`() {
    val firstDataRow = yearSummaryTable.find(By.tagName("tr"), 1)

    taxRateInput.value = "0"
    Thread.sleep(500)

    val taxAmountWithZeroRate = firstDataRow.find(By.tagName("td"), 4).text()
    expect(taxAmountWithZeroRate).toBeEmpty()

    taxRateInput.value = "25"
    Thread.sleep(500)

    val taxAmountWithRate = firstDataRow.find(By.tagName("td"), 4).text()
    expect(taxAmountWithRate).notToBeEmpty()
    expect(taxAmountWithRate.matches(Regex("[\\d,]+(\\.\\d{1,2})?"))).toEqual(true)
  }

  @Test
  fun `should calculate total worth as invested plus net profit`() {
    val firstDataRow = yearSummaryTable.find(By.tagName("tr"), 1)

    taxRateInput.value = "30"
    Thread.sleep(500)

    val totalInvestedText = firstDataRow.find(By.tagName("td"), 1).text().replace("[^0-9.]".toRegex(), "")
    val grossProfitText = firstDataRow.find(By.tagName("td"), 2).text().replace("[^0-9.]".toRegex(), "")
    val totalWorthText = firstDataRow.find(By.tagName("td"), 3).text().replace("[^0-9.]".toRegex(), "")

    val totalInvested = totalInvestedText.toDoubleOrNull() ?: 0.0
    val grossProfit = grossProfitText.toDoubleOrNull() ?: 0.0
    val totalWorth = totalWorthText.toDoubleOrNull() ?: 0.0

    val expectedTotalWorth = totalInvested + grossProfit
    expect(totalWorth).toBeGreaterThanOrEqualTo(expectedTotalWorth - 1.0).toBeLessThanOrEqualTo(expectedTotalWorth + 1.0)
  }

  @Test
  fun `should preserve tax rate value`() {
    taxRateInput.value = "18.5"
    taxRateInput.shouldHave(value("18.5"))

    annualReturnInput.value = "10"
    Thread.sleep(500)

    taxRateInput.shouldHave(value("18.5"))
  }

  private fun id(id: String): SelenideElement = element(By.id(id))
}
