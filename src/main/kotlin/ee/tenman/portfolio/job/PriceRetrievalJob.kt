package ee.tenman.portfolio.job

import com.codeborne.selenide.Condition
import com.codeborne.selenide.Configuration
import com.codeborne.selenide.Selenide
import ee.tenman.portfolio.service.InstrumentService
import org.openqa.selenium.By
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class PriceRetrievalJob(
  private val instrumentService: InstrumentService
) {
  private val log = LoggerFactory.getLogger(javaClass)

  init {
    Configuration.browser = "firefox"
    Configuration.headless = true
  }

  @Scheduled(cron = "0 0/2 * * * *")
  fun fetchCurrentPrices() {
    log.info("Fetching current prices")
    val instruments = instrumentService.getAllInstruments()
    instruments.forEach { instrument ->
      try {
        if (instrument.symbol.contains("QDVE")) {
          Selenide.open("https://markets.ft.com/data/etfs/tearsheet/summary?s=QDVE:GER:EUR")
          Selenide.sleep(3000)
          val elements = Selenide.elements("iframe")
          if (elements.size() == 4) {
            Selenide.switchTo().frame(elements.last())
            Selenide.elements("button").find(Condition.text("Accept")).click()
            Selenide.switchTo().defaultContent()
          }
          val priceText = Selenide.element(By.className("mod-ui-data-list__value")).text()
          val price = BigDecimal(priceText.replace(",", ""))

          instrument.currentPrice = price

          instrumentService.saveInstrument(instrument)
          log.info("${instrument.name} current price: $price")
        }
      } catch (e: Exception) {
        log.error("Error retrieving current price for ${instrument.name}", e)
      } finally {
        Selenide.closeWindow()
        Selenide.closeWebDriver()
      }
    }
    log.info("Completed fetching current prices")
  }
}
