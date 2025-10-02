package e2e

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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import java.time.Duration

private const val CALCULATOR_BASE_URL = "http://localhost:61234/calculator"

@ExtendWith(RetryExtension::class)
@Retry(times = 3, onExceptions = [ElementNotFound::class, TimeoutException::class])
class CalculatorE2ETests {
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
    assertThat(initialTotalWorth).isNotEmpty()

    annualReturnInput.value = "50"

    firstDataRow.find(By.tagName("td"), 2).shouldNotHave(text(initialTotalWorth), Duration.ofSeconds(5))

    val updatedTotalWorth = firstDataRow.find(By.tagName("td"), 2).text()
    assertThat(updatedTotalWorth).isNotEqualTo(initialTotalWorth)
    assertThat(firstDataRow.find(By.tagName("td"), 3).text()).isNotEmpty()
  }

  @Test
  fun `should preserve user-modified annual return rate`() {
    annualReturnInput.value = "35.5"
    annualReturnInput.shouldHave(value("35.5"))
  }

  @Test
  fun `should reset annual return rate when reset button is clicked`() {
    val initialValue = annualReturnInput.value
    assertThat(initialValue).isNotNull().isNotEmpty()

    annualReturnInput.value = "75"
    annualReturnInput.shouldHave(value("75"))

    resetButton.click()
    val confirmButton = element(By.xpath("//button[contains(@class, 'btn-warning') and contains(text(), 'Reset')]"))
    confirmButton.shouldBe(visible, Duration.ofSeconds(5)).click()

    annualReturnInput.shouldHave(value(initialValue!!), Duration.ofSeconds(5))
  }

  @Test
  fun `should recalculate when tax rate is changed`() {
    val firstDataRow = yearSummaryTable.find(By.tagName("tr"), 1)
    val initialNetProfit = firstDataRow.find(By.tagName("td"), 5).text()
    assertThat(initialNetProfit).isNotEmpty()

    taxRateInput.value = "50"

    firstDataRow.find(By.tagName("td"), 5).shouldNotHave(text(initialNetProfit), Duration.ofSeconds(5))

    val updatedNetProfit = firstDataRow.find(By.tagName("td"), 5).text()
    assertThat(updatedNetProfit).isNotEqualTo(initialNetProfit)
  }

  @Test
  fun `should display tax amount column correctly`() {
    val firstDataRow = yearSummaryTable.find(By.tagName("tr"), 1)

    taxRateInput.value = "0"
    Thread.sleep(500)

    val taxAmountWithZeroRate = firstDataRow.find(By.tagName("td"), 4).text()
    assertThat(taxAmountWithZeroRate).isEmpty()

    taxRateInput.value = "25"
    Thread.sleep(500)

    val taxAmountWithRate = firstDataRow.find(By.tagName("td"), 4).text()
    assertThat(taxAmountWithRate).isNotEmpty()
    assertThat(taxAmountWithRate).matches("[\\d,]+(\\.\\d{1,2})?")
  }

  @Test
  fun `should calculate total worth as invested plus net profit`() {
    val firstDataRow = yearSummaryTable.find(By.tagName("tr"), 1)

    taxRateInput.value = "30"
    Thread.sleep(500)

    val totalInvestedText = firstDataRow.find(By.tagName("td"), 1).text().replace("[^0-9.]".toRegex(), "")
    val totalWorthText = firstDataRow.find(By.tagName("td"), 2).text().replace("[^0-9.]".toRegex(), "")
    val netProfitText = firstDataRow.find(By.tagName("td"), 5).text().replace("[^0-9.]".toRegex(), "")

    val totalInvested = totalInvestedText.toDoubleOrNull() ?: 0.0
    val totalWorth = totalWorthText.toDoubleOrNull() ?: 0.0
    val netProfit = netProfitText.toDoubleOrNull() ?: 0.0

    val expectedTotalWorth = totalInvested + netProfit
    assertThat(totalWorth).isCloseTo(expectedTotalWorth, within(1.0))
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
