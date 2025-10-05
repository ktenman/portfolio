package e2e

import com.codeborne.selenide.Condition.enabled
import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Condition.value
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Selenide.clearBrowserLocalStorage
import com.codeborne.selenide.Selenide.element
import com.codeborne.selenide.Selenide.elements
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
import org.openqa.selenium.By.className
import org.openqa.selenium.By.tagName
import org.openqa.selenium.Keys
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
    val addButton = id("addNewInstrument").shouldBe(visible, Duration.ofSeconds(10))
    assertThat(addButton.isDisplayed).isTrue()
    addButton.shouldBe(enabled).click()

    val symbolField = id("symbol").shouldBe(visible, Duration.ofSeconds(5))
    symbolField.shouldBe(enabled)
    symbolField.shouldNotHave(text(DEFAULT_SYMBOL)).value = DEFAULT_SYMBOL
    assertThat(symbolField.value).isEqualTo(DEFAULT_SYMBOL)

    val nameField = id("name")
    nameField.shouldBe(enabled)
    nameField.shouldNotHave(text(DEFAULT_NAME)).value = DEFAULT_NAME
    assertThat(nameField.value).isEqualTo(DEFAULT_NAME)

    id("category").shouldBe(enabled).selectOption(DEFAULT_CATEGORY)
    id("providerName").shouldBe(enabled).selectOption("Binance")

    val currencyField = id("baseCurrency")
    currencyField.shouldBe(visible)
    currencyField.shouldHave(value("EUR"))

    val saveButton = elements(tagName("button")).filter(text("Save")).first()
    assertThat(saveButton.isEnabled).isTrue()
    saveButton.click()

    val successAlert = element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    successAlert.shouldHave(text("Instrument saved successfully."))

    elements(tagName("td")).findBy(text(DEFAULT_SYMBOL)).shouldBe(visible)
  }

  @Test
  fun `should display success message when editing instrument name and price`() {
    id("addNewInstrument").click()
    id("symbol").shouldNotHave(text("TSLA")).value = "TSLA"
    id("name").shouldNotHave(text("Tesla Inc.")).value = "Tesla Inc."
    id("category").selectOption("ETF")
    id("providerName").selectOption("Alpha Vantage")
    val priceField = id("currentPrice")
    priceField.shouldBe(visible)
    priceField.value = "250.00"
    elements(tagName("button")).filter(text("Save")).first().click()

    element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    Thread.sleep(1000)

    val editButtons = elements(By.cssSelector("button[title='Edit']"))
    assertThat(editButtons.size()).isGreaterThan(0)
    editButtons.first().click()

    val updatedName = "Tesla Motors Inc."
    val updatedPrice = "275.50"

    val symbolField = id("symbol")
    symbolField.shouldBe(visible, Duration.ofSeconds(5))
    assertThat(symbolField.getAttribute("disabled")).isNotNull()

    val nameField = id("name")
    nameField.shouldBe(enabled)
    nameField.clear()
    nameField.value = updatedName
    nameField.shouldHave(value(updatedName))

    val currentPriceField = id("currentPrice")
    currentPriceField.shouldBe(enabled)
    currentPriceField.clear()
    currentPriceField.value = updatedPrice
    currentPriceField.shouldHave(value(updatedPrice))

    val categoryField = id("category")
    assertThat(categoryField.getAttribute("disabled")).isNotNull()

    val providerField = id("providerName")
    assertThat(providerField.getAttribute("disabled")).isNotNull()

    val currencyField = id("baseCurrency")
    assertThat(currencyField.getAttribute("disabled")).isNotNull()

    val updateButton = elements(tagName("button")).filter(text("Update")).first()
    assertThat(updateButton.isEnabled).isTrue()
    updateButton.click()

    val successAlert = element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    successAlert.shouldHave(text("Instrument updated successfully."))

    elements(tagName("td")).findBy(text(updatedName)).shouldBe(visible)
  }

  @Test
  fun `should save and update instrument with current price`() {
    id("addNewInstrument").click()
    id("symbol").shouldNotHave(text("BTC")).value = "BTC"
    id("name").shouldNotHave(text("Bitcoin")).value = "Bitcoin"
    id("category").selectOption("CRYPTOCURRENCY")
    id("providerName").selectOption("Binance")

    val priceField = id("currentPrice")
    priceField.shouldBe(visible)
    priceField.value = "45000.50"

    elements(tagName("button")).filter(text("Save")).first().click()

    element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    Thread.sleep(1000)

    val editButtons = elements(By.cssSelector("button[title='Edit']"))
    assertThat(editButtons.size()).isGreaterThan(0)
    editButtons.last().click()

    val currentPriceField = id("currentPrice")
    currentPriceField.shouldBe(visible, Duration.ofSeconds(5))
    currentPriceField.shouldBe(enabled)
    currentPriceField.shouldHave(value("45000.5"))

    currentPriceField.clear()
    currentPriceField.value = "48500.75"

    val nameField = id("name")
    nameField.shouldBe(enabled)
    nameField.clear()
    nameField.value = "Bitcoin (Updated)"

    val updateButton = elements(tagName("button")).filter(text("Update")).first()
    updateButton.click()

    val successAlert = element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    successAlert.shouldHave(text("Instrument updated successfully."))

    elements(tagName("td")).findBy(text("€48,500.75")).shouldBe(visible)
  }

  @Test
  fun `should validate symbol length requirements`() {
    id("addNewInstrument").click()

    val symbolField = id("symbol")
    symbolField.shouldBe(visible, Duration.ofSeconds(5))
    symbolField.value = "A"
    symbolField.sendKeys(Keys.TAB)

    val symbolError =
      element(By.xpath("//div[contains(@class, 'invalid-feedback') and contains(text(), 'Symbol must be at least 2 characters')]"))
    symbolError.shouldBe(visible)

    symbolField.clear()
    symbolField.value = "AA"
    symbolField.sendKeys(Keys.TAB)

    symbolError.shouldNotBe(visible)

    symbolField.clear()
    symbolField.value = "VERYLONGSYMBOLNAME"
    symbolField.sendKeys(Keys.TAB)

    symbolError.shouldNotBe(visible)
  }

  private fun id(id: String): SelenideElement = element(By.id(id))
}
