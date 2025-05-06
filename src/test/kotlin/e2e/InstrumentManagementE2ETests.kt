package e2e

import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Selenide.*
import com.codeborne.selenide.SelenideElement
import com.codeborne.selenide.ex.ElementNotFound
import e2e.retry.Retry
import e2e.retry.RetryExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
import org.openqa.selenium.By.className
import org.openqa.selenium.By.tagName
import org.openqa.selenium.TimeoutException
import java.time.Duration

private const val INSTRUMENTS_BASE_URL = "http://localhost:61234/instruments"
private const val DEFAULT_SYMBOL = "AAPL"
private const val DEFAULT_NAME = "Apple Inc."
private const val DEFAULT_CATEGORY = "Stock"
private const val DEFAULT_CURRENCY = "USD"

@ExtendWith(RetryExtension::class)
@Retry(times = 3, onExceptions = [ElementNotFound::class, TimeoutException::class])
class InstrumentManagementE2ETests {

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
  fun `should display success message when saving instrument with valid data`() {

    id("addNewInstrument").click()
    id("symbol").shouldNotHave(text(DEFAULT_SYMBOL)).setValue(DEFAULT_SYMBOL)
    id("name").shouldNotHave(text(DEFAULT_NAME)).setValue(DEFAULT_NAME)
    id("category").selectOption(DEFAULT_CATEGORY)
    id("providerName").selectOption("Binance")
    id("currency").selectOption(DEFAULT_CURRENCY)

    elements(tagName("button")).filter(text("Save")).first().click()

    element(className("alert-success"))
      .shouldBe(visible, Duration.ofSeconds(10))
      .shouldHave(text("Instrument saved successfully."))
  }

  @Test
  fun `should display success message when editing instrument with valid data`() {

    elements(tagName("button")).filter(text("Edit")).first().click()
    id("symbol").shouldNotHave(text(DEFAULT_SYMBOL)).setValue("GOOGL")
    id("name").shouldNotHave(text(DEFAULT_NAME)).setValue("Alphabet Inc.")
    id("category").selectOption("Stock")
    id("providerName").selectOption("Alpha Vantage")
    id("currency").selectOption("USD")

    elements(tagName("button")).filter(text("Update")).first().click()

    element(className("alert-success"))
      .shouldBe(visible, Duration.ofSeconds(10))
      .shouldHave(text("Instrument updated successfully."))
  }

  private fun id(id: String): SelenideElement = element(By.id(id))

}
