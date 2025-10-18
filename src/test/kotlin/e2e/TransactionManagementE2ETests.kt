package e2e

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toContain
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
import org.junit.jupiter.api.Disabled
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

  @Disabled("Disabled due to hidden UI elements - Add New Transaction button")
  @Test
  fun `should display success message after saving a new transaction`() {
    val addButton = id("addNewTransaction")
    expect(addButton.isDisplayed).toEqual(true)
    addButton.shouldBe(enabled).click()

    val expectedQuantity = "10.144"
    val expectedPrice = "29.615"
    val expectedDate = "2024-07-10"

    val instrumentSelect = id("instrumentId").shouldBe(visible, Duration.ofSeconds(5))
    val options = instrumentSelect.findAll("option")
    expect(options.size()).toBeGreaterThan(1)
    instrumentSelect.selectOption(1)

    id("transactionType").selectOption("BUY")

    val quantityField = id("quantity")
    quantityField.value = expectedQuantity
    expect(quantityField.value).toEqual(expectedQuantity)

    val priceField = id("price")
    priceField.value = expectedPrice
    expect(priceField.value).toEqual(expectedPrice)

    val dateField = id("transactionDate")
    dateField.clear()
    dateField.sendKeys(expectedDate)
    dateField.shouldHave(value(expectedDate), Duration.ofSeconds(2))

    id("platform").selectOption("TRADING212")

    val saveButton = elements(tagName("button")).filter(text("Save")).first()
    expect(saveButton.isEnabled).toEqual(true)
    saveButton.click()

    val successAlert =
      element(className("alert-success"))
        .shouldBe(visible, Duration.ofSeconds(10))
    successAlert.shouldHave(text("Transaction saved successfully."))

    val savedTransaction = elements(tagName("td")).findBy(text("10.14"))
    expect(savedTransaction.isDisplayed).toEqual(true)
    savedTransaction.shouldBe(visible)

    val transactionRow = savedTransaction.closest("tr")
    val rowCells = transactionRow.findAll(tagName("td"))

    val cellTexts = rowCells.texts()
    expect(cellTexts.size).toBeGreaterThan(3)
    expect(cellTexts.any { it.contains("10.14") }).toEqual(true)
    expect(cellTexts.any { it.contains("29.6") }).toEqual(true)
  }

  @Disabled("Disabled due to hidden UI elements - Edit button")
  @Test
  fun `should display success message after editing an existing transaction`() {
    id("addNewTransaction").click()
    val instrumentSelect = id("instrumentId").shouldBe(visible, Duration.ofSeconds(5))
    instrumentSelect.selectOption(1)
    id("transactionType").selectOption("BUY")
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
    expect(editButtons.size()).toBeGreaterThan(0)
    editButtons.first().click()

    val updatedQuantity = "112255.1211"
    val updatedPrice = "332211.189"
    val updatedDate = "2025-07-07"

    id("instrumentId").shouldBe(visible, Duration.ofSeconds(5))
    id("instrumentId").selectOption(1)
    id("transactionType").selectOption("SELL")

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
    expect(updateButton.isEnabled).toEqual(true)
    updateButton.click()

    val successAlert =
      element(className("alert-success"))
        .shouldBe(visible, Duration.ofSeconds(10))
    successAlert.shouldHave(text("Transaction updated successfully."))

    val updatedQuantityCell = elements(tagName("td")).findBy(text("112255.12"))
    expect(updatedQuantityCell.isDisplayed).toEqual(true)

    val transactionRow = updatedQuantityCell.closest("tr")
    val transactionDetails = transactionRow.findAll(tagName("td"))
    expect(transactionDetails.isEmpty()).toEqual(false)
    expect(transactionDetails.size()).toBeGreaterThan(5)

    val cellTexts = transactionDetails.texts()
    expect(cellTexts.any { it.contains("SELL") }).toEqual(true)
    expect(cellTexts.any { it.contains("112255.12") }).toEqual(true)
    expect(cellTexts.any { it.contains("332211.19") }).toEqual(true)
    expect(cellTexts.any { it.contains("07.07.25") || it.contains("2025-07-07") }).toEqual(true)

    val actionsCell = transactionDetails.last()
    expect(actionsCell.text().lowercase()).toContain("edit")
    expect(actionsCell.text().lowercase()).toContain("delete")
  }

  @Disabled("Disabled due to hidden UI elements - Delete button")
  @Test
  fun `should display success message after deleting a transaction`() {
    id("addNewTransaction").click()
    val instrumentSelect = id("instrumentId").shouldBe(visible, Duration.ofSeconds(5))
    instrumentSelect.selectOption(1)
    id("transactionType").selectOption("SELL")
    id("quantity").value = "99.99"
    id("price").value = "999.99"
    val createDate = "2024-12-25"
    id("transactionDate").clear()
    id("transactionDate").sendKeys(createDate)
    id("platform").selectOption("BINANCE")
    elements(tagName("button")).filter(text("Save")).first().click()

    element(className("alert-success")).shouldBe(visible, Duration.ofSeconds(10))
    Thread.sleep(1000)

    val createdTransaction = elements(tagName("td")).findBy(text("99.99"))
    expect(createdTransaction.isDisplayed).toEqual(true)

    val transactionRow = createdTransaction.closest("tr")
    val deleteButton = transactionRow.findAll(tagName("button")).filter(text("Delete")).first()
    expect(deleteButton.isEnabled).toEqual(true)
    deleteButton.click()

    val confirmButton = elements(tagName("button")).filter(text("Confirm")).first()
    confirmButton.shouldBe(visible, Duration.ofSeconds(5)).click()

    val successAlert =
      element(className("alert-success"))
        .shouldBe(visible, Duration.ofSeconds(10))
    successAlert.shouldHave(text("Transaction deleted successfully."))

    Thread.sleep(1000)
    val deletedTransactions = elements(tagName("td")).filter(text("99.99"))
    expect(deletedTransactions.size()).toEqual(0)
  }

  @Disabled("Platform filter UI testing needs additional setup")
  @Test
  fun `should display platform filter buttons on transactions page`() {
    val platformButtons = elements(className("platform-btn"))
    expect(platformButtons.size()).toBeGreaterThan(0)
    platformButtons.forEach { button ->
      expect(button.isDisplayed).toEqual(true)
    }
  }

  @Disabled("Platform filter UI testing needs additional setup")
  @Test
  fun `should filter transactions by platform using filter buttons`() {
    val platformButtons = elements(className("platform-btn"))
    expect(platformButtons.size()).toBeGreaterThan(1)

    val firstPlatformButton = platformButtons.first()
    val initialButtonText = firstPlatformButton.text
    expect(initialButtonText.isEmpty()).toEqual(false)

    firstPlatformButton.click()
    Thread.sleep(500)

    val activeButtons = elements(className("platform-btn active"))
    expect(activeButtons.size()).toBeGreaterThan(0)

    val selectedButtonsActive = activeButtons.any { it.text.contains(initialButtonText) }
    expect(selectedButtonsActive).toEqual(true)
  }

  @Disabled("Platform filter UI testing needs additional setup")
  @Test
  fun `should have select all and clear all buttons in platform filter`() {
    val platformButtons = elements(className("platform-btn"))
    expect(platformButtons.size()).toBeGreaterThan(2)

    val selectAllButton = platformButtons.findBy(text("Select All"))
    expect(selectAllButton.isDisplayed).toEqual(true)

    selectAllButton.click()
    Thread.sleep(500)

    val activeButtons = elements(className("platform-btn active"))
    val selectAllOrClearAllButtons =
      activeButtons.filter {
        it.text.contains("Select All") || it.text.contains("Clear All")
      }
    expect(selectAllOrClearAllButtons.size).toBeGreaterThan(0)
  }

  @Disabled("Platform filter UI testing needs additional setup")
  @Test
  fun `should maintain platform filter selection after page reload`() {
    val platformButtons = elements(className("platform-btn"))
    expect(platformButtons.size()).toBeGreaterThan(1)

    val firstPlatformButton = platformButtons.first()
    firstPlatformButton.click()
    Thread.sleep(500)

    val activeButtonsBefore = elements(className("platform-btn active"))
    val activeCountBefore = activeButtonsBefore.size()
    expect(activeCountBefore).toBeGreaterThan(0)

    open(TRANSACTIONS_BASE_URL)
    Thread.sleep(1000)

    val activeButtonsAfter = elements(className("platform-btn active"))
    expect(activeButtonsAfter.size()).toEqual(activeCountBefore)
  }

  private fun id(id: String): SelenideElement = element(By.id(id))
}
