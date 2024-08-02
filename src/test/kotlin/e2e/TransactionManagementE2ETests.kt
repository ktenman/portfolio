package e2e

import com.codeborne.selenide.Condition
import com.codeborne.selenide.Selenide
import com.codeborne.selenide.SelenideElement
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.By

private const val TRANSACTIONS_BASE_URL = "http://localhost:61234/transactions"

class TransactionManagementE2ETests {

  @BeforeEach
  fun setUp() {
    Selenide.open(TRANSACTIONS_BASE_URL)
  }

  @AfterEach
  fun tearDown() {
    Selenide.clearBrowserLocalStorage()
  }

  @Test
  fun `should display success message after saving a new transaction`() {
    id("addNewTransaction").click()
    id("instrumentId").selectOption("AAPL - Apple Inc.")
    id("transactionType").selectOption("Buy")
    id("quantity").setValue("10.144")
    id("price").setValue("29.615")
    id("transactionDate").setValue("10.07.2024")

    Selenide.elements(By.tagName("button")).filter(Condition.text("Save")).first().click()

    Selenide.sleep(2000)

    val alertMessage = Selenide.element(By.className("alert-success"))
    if (alertMessage.exists()) {
      assertThat(alertMessage.text).isEqualTo("Transaction saved successfully.")
    } else {
      fail("Alert message not found.")
    }

    val qdveTransaction = Selenide.elements(By.tagName("td")).findBy(Condition.text("AAPL"))
    if (qdveTransaction.exists()) {
      assertThat(qdveTransaction.text).isEqualTo("AAPL")
    } else {
      fail("Transaction not found.")
    }
  }

  @Test
  fun `should display success message after editing an existing transaction`() {
    Selenide.elements(By.tagName("button")).filter(Condition.text("Edit")).first().click()
    id("instrumentId").selectOption("AAPL - Apple Inc.")
    id("transactionType").selectOption("Sell")
    id("quantity").setValue("112255.1211")
    id("price").setValue("332211.189")
    id("transactionDate").setValue("07.07.2025")

    Selenide.elements(By.tagName("button")).filter(Condition.text("Update")).first().click()

    Selenide.sleep(2000)

    val alertMessage = Selenide.element(By.className("alert-success"))
    if (alertMessage.exists()) {
      assertThat(alertMessage.text).isEqualTo("Transaction updated successfully.")
    } else {
      fail("Alert message not found.")
    }

    val transactionDetails = Selenide.elements(By.tagName("td")).findBy(Condition.text("112255.12"))
      .closest("tr").findAll(By.tagName("td"))

    assertThat(transactionDetails.texts()).containsExactlyInAnyOrder(
      "AAPL",
      "SELL",
      "112255.12",
      "332211.19",
      "07.07.25",
      "EditDelete",
    )
  }

  private fun id(id: String): SelenideElement = Selenide.element(By.id(id))


}
