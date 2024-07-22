package e2e

import com.codeborne.selenide.Condition
import com.codeborne.selenide.Selenide
import com.codeborne.selenide.SelenideElement
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openqa.selenium.By

private const val TRANSACTIONS_BASE_URL = "http://localhost:61234/transactions"

class TransactionManagementE2ETests {

  @AfterEach
  fun tearDown() {
    Selenide.clearBrowserLocalStorage()
  }

  @Disabled
  @Test
  fun `should display success message after saving a new transaction`() {
    Selenide.open(TRANSACTIONS_BASE_URL)

    id("addNewTransaction").click()
    id("instrumentId").selectOption("QDVE.DEX - iShares S&P 500 Information Technology Sector")
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

    val qdveTransaction = Selenide.elements(By.tagName("td")).findBy(Condition.text("QDVE.DEX"))
    if (qdveTransaction.exists()) {
      assertThat(qdveTransaction.text).isEqualTo("QDVE.DEX")
    } else {
      fail("Transaction not found.")
    }
  }

  @Disabled
  @Test
  fun `should display success message after editing an existing transaction`() {
    Selenide.open(TRANSACTIONS_BASE_URL)

    Selenide.elements(By.tagName("button")).filter(Condition.text("Edit")).first().click()
    id("instrumentId").selectOption("QDVE.DEX - iShares S&P 500 Information Technology Sector")
    id("transactionType").selectOption("Sell")
    id("quantity").setValue("112255.1211")
    id("price").setValue("332211.189")
    id("transactionDate").setValue("11.07.2025")

    Selenide.elements(By.tagName("button")).filter(Condition.text("Update")).first().click()

    Selenide.sleep(1000)

    val alertMessage = Selenide.element(By.className("alert-success"))
    if (alertMessage.exists()) {
      assertThat(alertMessage.text).isEqualTo("Transaction updated successfully.")
    } else {
      fail("Alert message not found.")
    }

    val transactionDetails = Selenide.elements(By.tagName("td")).findBy(Condition.text("112255.12"))
      .closest("tr").findAll(By.tagName("td"))

    assertThat(transactionDetails.texts()).containsExactlyInAnyOrder(
      "QDVE.DEX",
      "SELL",
      "112255.12",
      "332211.19",
      "11.07.25",
      "EditDelete",
    )
  }

  private fun id(id: String): SelenideElement = Selenide.element(By.id(id))
}
