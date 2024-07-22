package e2e

import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Selenide
import com.codeborne.selenide.Selenide.clearBrowserLocalStorage
import com.codeborne.selenide.Selenide.open
import com.codeborne.selenide.SelenideElement
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import org.openqa.selenium.By.className
import org.openqa.selenium.By.tagName

private const val INSTRUMENTS_BASE_URL = "http://localhost:61234/instruments"
private const val DEFAULT_SYMBOL = "AAPL"
private const val DEFAULT_NAME = "Apple Inc."
private const val DEFAULT_CATEGORY = "Stock"
private const val DEFAULT_CURRENCY = "USD"

class InstrumentSavingE2ETest {

  @AfterEach
  fun tearDown() {
    clearBrowserLocalStorage()
  }

  @Test
  fun `should display success message when saving instrument with valid data`() {
    open(INSTRUMENTS_BASE_URL)

    id("addNewInstrument").click()
    id("symbol").shouldNotHave(text(DEFAULT_SYMBOL)).setValue(DEFAULT_SYMBOL)
    id("name").shouldNotHave(text(DEFAULT_NAME)).setValue(DEFAULT_NAME)
    id("category").selectOption(DEFAULT_CATEGORY)
    id("currency").selectOption(DEFAULT_CURRENCY)

    Selenide.elements(tagName("button")).filter(text("Save")).first().click()

    Selenide.sleep(1000)

    val alertMessage = Selenide.element(className("alert-success"))
    if (alertMessage.exists()) {
      assertThat(alertMessage.text).isEqualTo("Instrument saved successfully.")
    } else {
      fail("Alert message not found.")
    }
  }

  @Test
  fun `should display success message when editing instrument with valid data`() { // Method name updated
    open(INSTRUMENTS_BASE_URL)

    Selenide.elements(tagName("button")).filter(text("Edit")).first().click()
    id("symbol").shouldNotHave(text(DEFAULT_SYMBOL)).setValue("GOOGL")
    id("name").shouldNotHave(text(DEFAULT_NAME)).setValue("Alphabet Inc.")
    id("category").selectOption("Stock")
    id("currency").selectOption("USD")

    Selenide.elements(tagName("button")).filter(text("Update")).first().click()

    Selenide.sleep(1000)

    val alertMessage = Selenide.element(className("alert-success"))
    if (alertMessage.exists()) {
      assertThat(alertMessage.text).isEqualTo("Instrument updated successfully.")
    } else {
      fail("Alert message not found.")
    }
  }

  private fun id(id: String): SelenideElement = Selenide.element(By.id(id))
}
