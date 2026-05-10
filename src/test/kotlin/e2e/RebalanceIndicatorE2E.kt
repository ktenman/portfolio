package e2e

import com.codeborne.selenide.Condition.disappear
import com.codeborne.selenide.Condition.value
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Selenide.clearBrowserLocalStorage
import com.codeborne.selenide.Selenide.element
import com.codeborne.selenide.Selenide.open
import com.codeborne.selenide.SelenideElement
import com.codeborne.selenide.ex.ElementNotFound
import e2e.retry.Retry
import e2e.retry.RetryExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.TimeoutException
import java.time.Duration

private const val DIVERSIFICATION_URL = "http://localhost:61234/diversification"

@ExtendWith(RetryExtension::class)
@Retry(times = 3, onExceptions = [ElementNotFound::class, TimeoutException::class])
class RebalanceIndicatorE2E {
  private val configureButton: SelenideElement by lazy {
    element("[data-testid=configure-thresholds]")
  }
  private val driftingInput: SelenideElement by lazy { element("#drifting-rel") }
  private val rebalanceRelInput: SelenideElement by lazy { element("#rebalance-rel") }
  private val rebalanceAbsInput: SelenideElement by lazy { element("#rebalance-abs") }
  private val saveButton: SelenideElement by lazy { element("button.save-btn") }
  private val resetButton: SelenideElement by lazy { element("button.reset-btn") }
  private val statusCard: SelenideElement by lazy { element(".rebalance-status-card") }

  @BeforeEach
  fun setUp() {
    BrowserConfig.configureBrowser()
    open(DIVERSIFICATION_URL)
    clearBrowserLocalStorage()
    open(DIVERSIFICATION_URL)
  }

  @AfterEach
  fun tearDown() {
    clearBrowserLocalStorage()
  }

  @Test
  fun `should keep modal open on input click and persist values after save and reopen`() {
    statusCard.shouldBe(visible, Duration.ofSeconds(15))
    configureButton.click()
    driftingInput.shouldBe(visible, Duration.ofSeconds(5))
    driftingInput.click()
    driftingInput.shouldBe(visible)
    driftingInput.value = "7"
    rebalanceRelInput.value = "20"
    rebalanceAbsInput.value = "3"
    saveButton.click()
    driftingInput.should(disappear)
    configureButton.click()
    driftingInput.shouldBe(visible, Duration.ofSeconds(5))
    driftingInput.shouldHave(value("7"))
    rebalanceRelInput.shouldHave(value("20"))
    rebalanceAbsInput.shouldHave(value("3"))
  }

  @Test
  fun `should restore default values when clicking reset and persist after save and reopen`() {
    statusCard.shouldBe(visible, Duration.ofSeconds(15))
    configureButton.click()
    driftingInput.shouldBe(visible, Duration.ofSeconds(5))
    driftingInput.value = "7"
    rebalanceRelInput.value = "20"
    rebalanceAbsInput.value = "3"
    saveButton.click()
    driftingInput.should(disappear)
    configureButton.click()
    driftingInput.shouldBe(visible, Duration.ofSeconds(5))
    resetButton.click()
    saveButton.click()
    driftingInput.should(disappear)
    configureButton.click()
    driftingInput.shouldBe(visible, Duration.ofSeconds(5))
    driftingInput.shouldHave(value("10"))
    rebalanceRelInput.shouldHave(value("25"))
    rebalanceAbsInput.shouldHave(value("5"))
  }
}
