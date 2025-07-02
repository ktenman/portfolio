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
    val initialTotalWorth = firstDataRow.find(By.tagName("td"), 1).text()
    assertThat(initialTotalWorth).isNotEmpty()

    annualReturnInput.value = "50"

    firstDataRow.find(By.tagName("td"), 1).shouldNotHave(text(initialTotalWorth), Duration.ofSeconds(5))

    val updatedTotalWorth = firstDataRow.find(By.tagName("td"), 1).text()
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

  private fun id(id: String): SelenideElement = element(By.id(id))
}
