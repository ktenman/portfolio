package e2e

import com.codeborne.selenide.Condition
import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Selectors.withText
import com.codeborne.selenide.Selenide
import com.codeborne.selenide.Selenide.element
import com.codeborne.selenide.Selenide.elements
import com.codeborne.selenide.Selenide.open
import com.codeborne.selenide.SelenideElement
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import org.openqa.selenium.By.className
import org.openqa.selenium.By.tagName

class InstrumentSavingE2ETest {

  companion object {
    private const val DEFAULT_SYMBOL = "AAPL"
    private const val DEFAULT_NAME = "Apple Inc."
    private const val DEFAULT_CATEGORY = "Stock"
    private const val DEFAULT_CURRENCY = "USD"
  }

  @Test
  fun `should display success message when saving instrument with valid data`() {
    open("http://localhost:61234/instruments")

    id("addNewInstrument").click()
    id("symbol").shouldNotHave(text(DEFAULT_SYMBOL)).setValue(DEFAULT_SYMBOL)
    id("name").shouldNotHave(text(DEFAULT_NAME)).setValue(DEFAULT_NAME)
    id("category").selectOption(DEFAULT_CATEGORY)
    id("currency").selectOption(DEFAULT_CURRENCY)

    elements(tagName("button")).filter(text("Save")).first().click()

    Selenide.sleep(1000)

    val alertMessage = element(className("alert-success"))
    if (alertMessage.exists()) {
      assertThat(alertMessage.text).isEqualTo("Instrument saved successfully.")
    } else {
      fail("Alert message not found.")
    }
  }

  private fun id(id: String): SelenideElement = element(By.id(id))
}
