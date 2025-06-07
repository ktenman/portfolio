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
    val addButton = id("addNewTransaction")
    assertThat(addButton.isDisplayed).isTrue()
    addButton.shouldBe(enabled).click()
    
    val expectedQuantity = "10.144"
    val expectedPrice = "29.615"
    val expectedDate = "2024-07-10"
    
    id("instrumentId").selectOption("AAPL - Apple Inc.")
    id("transactionType").selectOption("Buy")
    
    val quantityField = id("quantity")
    quantityField.value = expectedQuantity
    assertThat(quantityField.value).isEqualTo(expectedQuantity)
    
    val priceField = id("price")
    priceField.value = expectedPrice
    assertThat(priceField.value).isEqualTo(expectedPrice)
    
    val dateField = id("transactionDate")
    dateField.clear()
    dateField.sendKeys(expectedDate)
    dateField.shouldHave(value(expectedDate), Duration.ofSeconds(2))
    
    id("platform").selectOption("TRADING212")

    val saveButton = elements(tagName("button")).filter(text("Save")).first()
    assertThat(saveButton.isEnabled).isTrue()
    saveButton.click()

    val successAlert = element(className("alert-success"))
      .shouldBe(visible, Duration.ofSeconds(10))
    successAlert.shouldHave(text("Transaction saved successfully."))

    val savedTransaction = elements(tagName("td")).findBy(text("AAPL"))
    assertThat(savedTransaction.isDisplayed).isTrue()
    savedTransaction.shouldBe(visible).shouldHave(text("AAPL"))
    
    val transactionRow = savedTransaction.closest("tr")
    assertThat(transactionRow).isNotNull
    val rowCells = transactionRow.findAll(tagName("td"))
    
    val cellTexts = rowCells.texts()
    assertThat(cellTexts)
      .hasSizeGreaterThan(3)
      .anyMatch { it.contains("AAPL") }
      .anyMatch { it.contains("10.14") }
      .anyMatch { it.contains("29.6") }
  }

  @Test
  fun `should display success message after editing an existing transaction`() {
    id("addNewTransaction").click()
    id("instrumentId").selectOption("AAPL - Apple Inc.")
    id("transactionType").selectOption("Buy")
    id("quantity").value = "5.5"
    id("price").value = "150.25"
    val createDate = "2024-06-01"
    id("transactionDate").clear()
    id("transactionDate").sendKeys(createDate)
    id("platform").selectOption("TRADING212")
    elements(tagName("button")).filter(text("Save")).first().click()
    
    element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    Thread.sleep(1000)
    
    val editButtons = elements(tagName("button")).filter(text("Edit"))
    assertThat(editButtons.size()).isGreaterThan(0)
    editButtons.first().click()
    
    val updatedQuantity = "112255.1211"
    val updatedPrice = "332211.189"
    val updatedDate = "2025-07-07"
    
    id("instrumentId").shouldBe(visible, Duration.ofSeconds(5))
    id("instrumentId").selectOption("AAPL - Apple Inc.")
    id("transactionType").selectOption("Sell")
    
    val quantityField = id("quantity")
    quantityField.clear()
    quantityField.value = updatedQuantity
    quantityField.shouldHave(value(updatedQuantity))
    
    val priceField = id("price")
    priceField.clear()
    priceField.value = updatedPrice
    priceField.shouldHave(value(updatedPrice))
    
    val dateField = id("transactionDate")
    dateField.clear()
    dateField.sendKeys(updatedDate)
    dateField.shouldHave(value(updatedDate), Duration.ofSeconds(2))
    
    id("platform").selectOption("SWEDBANK")

    val updateButton = elements(tagName("button")).filter(text("Update")).first()
    assertThat(updateButton.isEnabled).isTrue()
    updateButton.click()

    val successAlert = element(className("alert-success"))
      .shouldBe(visible, Duration.ofSeconds(10))
    successAlert.shouldHave(text("Transaction updated successfully."))

    val updatedQuantityCell = elements(tagName("td")).findBy(text("112255.12"))
    assertThat(updatedQuantityCell.isDisplayed).isTrue()
    
    val transactionRow = updatedQuantityCell.closest("tr")
    assertThat(transactionRow).isNotNull
    
    val transactionDetails = transactionRow.findAll(tagName("td"))
    assertThat(transactionDetails)
      .isNotEmpty
      .hasSizeGreaterThan(5)
      
    val cellTexts = transactionDetails.texts()
    assertThat(cellTexts)
      .anyMatch { it.contains("AAPL") }
      .anyMatch { it.contains("SELL") }
      .anyMatch { it.contains("112255.12") }
      .anyMatch { it.contains("332211.19") }
      .anyMatch { it.contains("07.07.25") || it.contains("2025-07-07") }
      
    val actionsCell = transactionDetails.last()
    assertThat(actionsCell.text())
      .containsIgnoringCase("Edit")
      .containsIgnoringCase("Delete")
  }

  private fun id(id: String): SelenideElement = element(By.id(id))

}
