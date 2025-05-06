package e2e

import com.codeborne.selenide.Condition.text
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
import org.openqa.selenium.TimeoutException
import java.time.Duration

private const val TRANSACTIONS_BASE_URL = "http://localhost:61234/transactions"

@ExtendWith(RetryExtension::class)
@Retry(times = 3, onExceptions = [ElementNotFound::class, TimeoutException::class])
class TransactionManagementE2ETests {

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
  fun `should display success message after saving a new transaction`() {
    id("addNewTransaction").click()
    id("instrumentId").selectOption("AAPL - Apple Inc.")
    id("transactionType").selectOption("Buy")
    id("quantity").value = "10.144"
    id("price").value = "29.615"
    id("transactionDate").value = "10.07.2024"
    id("platform").selectOption("TRADING212")

    elements(tagName("button")).filter(text("Save")).first().click()

    element(className("alert-success"))
      .shouldBe(visible, Duration.ofSeconds(4))
      .shouldHave(text("Transaction saved successfully."))

    elements(tagName("td")).findBy(text("AAPL"))
      .shouldBe(visible)
      .shouldHave(text("AAPL"))
  }

  @Test
  fun `should display success message after editing an existing transaction`() {
    elements(tagName("button")).filter(text("Edit")).first().click()
    id("instrumentId").selectOption("AAPL - Apple Inc.")
    id("transactionType").selectOption("Sell")
    id("quantity").value = "112255.1211"
    id("price").value = "332211.189"
    id("transactionDate").value = "07.07.2025"
    id("platform").selectOption("SWEDBANK")

    elements(tagName("button")).filter(text("Update")).first().click()

    element(className("alert-success"))
      .shouldBe(visible, Duration.ofSeconds(4))
      .shouldHave(text("Transaction updated successfully."))

    val transactionDetails = elements(tagName("td")).findBy(text("112255.12"))
      .closest("tr").findAll(tagName("td"))

    assertThat(transactionDetails.texts()).containsExactlyInAnyOrder(
      "AAPL",
      "SELL",
      "112255.12",
      "332211.19",
      "07.07.25",
      "EditDelete",
    )
  }

  private fun id(id: String): SelenideElement = element(By.id(id))

}
