package e2e

import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Condition.value
import com.codeborne.selenide.Condition.enabled
import com.codeborne.selenide.Selenide.clearBrowserLocalStorage
import com.codeborne.selenide.Selenide.element
import com.codeborne.selenide.Selenide.elements
import com.codeborne.selenide.Selenide.open
import com.codeborne.selenide.SelenideElement
import com.codeborne.selenide.ex.ElementNotFound
import e2e.retry.Retry
import e2e.retry.RetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
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
private const val DEFAULT_CATEGORY = "ETF"
private const val DEFAULT_CURRENCY = "EUR"

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
    val addButton = id("addNewInstrument")
    assertThat(addButton.isDisplayed).isTrue()
    addButton.shouldBe(enabled).click()
    
    val symbolField = id("symbol").shouldBe(visible, Duration.ofSeconds(5))
    symbolField.shouldNotHave(text(DEFAULT_SYMBOL)).value = DEFAULT_SYMBOL
    assertThat(symbolField.value).isEqualTo(DEFAULT_SYMBOL)
    
    val nameField = id("name")
    nameField.shouldNotHave(text(DEFAULT_NAME)).value = DEFAULT_NAME
    assertThat(nameField.value).isEqualTo(DEFAULT_NAME)
    
    id("category").selectOption(DEFAULT_CATEGORY)
    id("providerName").selectOption("Binance")
    id("currency").selectOption(DEFAULT_CURRENCY)

    val saveButton = elements(tagName("button")).filter(text("Save")).first()
    assertThat(saveButton.isEnabled).isTrue()
    saveButton.click()

    val successAlert = element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    successAlert.shouldHave(text("Instrument saved successfully."))
      
    elements(tagName("td")).findBy(text(DEFAULT_SYMBOL)).shouldBe(visible)
  }

  @Test
  fun `should display success message when editing instrument with valid data`() {
    id("addNewInstrument").click()
    id("symbol").shouldNotHave(text("TSLA")).value = "TSLA"
    id("name").shouldNotHave(text("Tesla Inc.")).value = "Tesla Inc."
    id("category").selectOption("ETF")
    id("providerName").selectOption("Alpha Vantage")
    id("currency").selectOption("EUR")
    elements(tagName("button")).filter(text("Save")).first().click()
    
    element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    Thread.sleep(1000)
    
    val editButtons = elements(tagName("button")).filter(text("Edit"))
    assertThat(editButtons.size()).isGreaterThan(0)
    editButtons.first().click()
    
    val updatedSymbol = "GOOGL"
    val updatedName = "Alphabet Inc."
    
    val symbolField = id("symbol")
    symbolField.shouldBe(visible, Duration.ofSeconds(5))
    symbolField.clear()
    symbolField.value = updatedSymbol
    symbolField.shouldHave(value(updatedSymbol))
    
    val nameField = id("name")
    nameField.clear()
    nameField.value = updatedName
    nameField.shouldHave(value(updatedName))
    
    id("category").selectOption("ETF")
    id("providerName").selectOption("Alpha Vantage")
    id("currency").selectOption("EUR")

    val updateButton = elements(tagName("button")).filter(text("Update")).first()
    assertThat(updateButton.isEnabled).isTrue()
    updateButton.click()

    val successAlert = element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    successAlert.shouldHave(text("Instrument updated successfully."))
      
    elements(tagName("td")).findBy(text(updatedSymbol)).shouldBe(visible)
  }

  private fun id(id: String): SelenideElement = element(By.id(id))
}
