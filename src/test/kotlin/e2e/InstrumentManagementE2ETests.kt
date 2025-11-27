package e2e

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
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
    expect(addButton.isDisplayed).toEqual(true)
    addButton.shouldBe(enabled).click()

    val symbolField = id("symbol").shouldBe(visible, Duration.ofSeconds(5))
    symbolField.shouldBe(enabled)
    symbolField.shouldNotHave(text(DEFAULT_SYMBOL)).value = DEFAULT_SYMBOL
    expect(symbolField.value).toEqual(DEFAULT_SYMBOL)

    val nameField = id("name")
    nameField.shouldBe(enabled)
    nameField.shouldNotHave(text(DEFAULT_NAME)).value = DEFAULT_NAME
    expect(nameField.value).toEqual(DEFAULT_NAME)

    id("category").shouldBe(enabled).selectOption(DEFAULT_CATEGORY)
    id("providerName").shouldBe(enabled).selectOption("Binance")

    val currencyField = id("baseCurrency")
    currencyField.shouldBe(visible)
    currencyField.shouldHave(value(DEFAULT_CURRENCY))

    val saveButton = elements(tagName("button")).filter(text("Save")).first()
    expect(saveButton.isEnabled).toEqual(true)
    saveButton.click()

    val successAlert = element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    successAlert.shouldHave(text("Instrument saved successfully."))

    elements(tagName("td")).findBy(text(DEFAULT_SYMBOL)).shouldBe(visible)
  }

  @Test
  fun `should display success message when editing instrument price`() {
    id("addNewInstrument").click()
    id("symbol").shouldNotHave(text("TSLA")).value = "TSLA"
    id("name").shouldNotHave(text("Tesla Inc.")).value = "Tesla Inc."
    id("category").selectOption("ETF")
    id("providerName").selectOption("FT")
    val priceField = id("currentPrice")
    priceField.shouldBe(visible)
    priceField.value = "250.00"
    elements(tagName("button")).filter(text("Save")).first().click()

    element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    Thread.sleep(1000)

    val editButtons = elements(By.cssSelector("button[title='Edit']"))
    expect(editButtons.size()).toBeGreaterThan(0)
    editButtons.first().click()

    val updatedPrice = "275.50"

    val symbolField = id("symbol")
    symbolField.shouldBe(visible, Duration.ofSeconds(5))
    expect(symbolField.getAttribute("disabled")).notToEqualNull()

    val nameField = id("name")
    nameField.shouldBe(visible)
    expect(nameField.getAttribute("disabled")).notToEqualNull()

    val currentPriceField = id("currentPrice")
    currentPriceField.shouldBe(enabled)
    currentPriceField.clear()
    currentPriceField.value = updatedPrice
    currentPriceField.shouldHave(value(updatedPrice))

    val categoryField = id("category")
    expect(categoryField.getAttribute("disabled")).notToEqualNull()

    val providerField = id("providerName")
    expect(providerField.getAttribute("disabled")).notToEqualNull()

    val currencyField = id("baseCurrency")
    expect(currencyField.getAttribute("disabled")).notToEqualNull()

    val updateButton = elements(tagName("button")).filter(text("Update")).first()
    expect(updateButton.isEnabled).toEqual(true)
    updateButton.click()

    val successAlert = element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    successAlert.shouldHave(text("Instrument updated successfully."))

    elements(tagName("td")).findBy(text("Tesla Inc.")).shouldBe(visible)
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
    expect(editButtons.size()).toBeGreaterThan(0)
    editButtons.last().click()

    val currentPriceField = id("currentPrice")
    currentPriceField.shouldBe(visible, Duration.ofSeconds(5))
    currentPriceField.shouldBe(enabled)
    currentPriceField.shouldHave(value("45000.5"))

    currentPriceField.clear()
    currentPriceField.value = "48500.75"

    val nameField = id("name")
    nameField.shouldBe(visible)
    expect(nameField.getAttribute("disabled")).notToEqualNull()

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
