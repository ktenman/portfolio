package ee.tenman.portfolio.job

import com.codeborne.selenide.Configuration
import com.codeborne.selenide.Selenide.elements
import com.codeborne.selenide.Selenide.open
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.service.JobTransactionService
import org.openqa.selenium.By.className
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

private const val LIGHTYEAR_URL = "https://lightyear.com/en/etf/VUAA:XETRA/holdings/1"

@Component
@ConditionalOnProperty(name = ["scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class LightyearDataFetchJob(
  private val jobTransactionService: JobTransactionService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = 30000, fixedDelay = Long.MAX_VALUE)
  fun runJob() {
    log.info("Running Lightyear data fetch job (30 seconds after startup)")
    val startTime = Instant.now()
    var status = JobStatus.SUCCESS
    var message: String? = null

    try {
      message = fetchData()
      log.info("Completed Lightyear data fetch job successfully")
    } catch (e: Exception) {
      status = JobStatus.FAILURE
      message = "Failed to fetch data: ${e.message}"
      log.error("Lightyear data fetch job failed", e)
    } finally {
      val endTime = Instant.now()
      jobTransactionService.saveJobExecution(
        job = this,
        startTime = startTime,
        endTime = endTime,
        status = status,
        message = message,
      )
    }
  }

  override fun execute() {
    fetchData()
  }

  private fun fetchData(): String {
    log.info("Starting Lightyear data fetch job")
    configureBrowser()

    val fetchedData = mutableListOf<String>()

    open(LIGHTYEAR_URL)
    log.info("Opened Lightyear ETF holdings page")

    val tableRows =
      elements(className("table-row"))
      .filter { it.text.contains("%") }
      .take(5)

    tableRows.forEach { row ->
      val rowText = row.text()
      fetchedData.add(rowText)
      log.info("Fetched holding: $rowText")
    }

    val resultMessage = "Successfully fetched ${fetchedData.size} holdings:\n${fetchedData.joinToString("\n")}"
    log.info(resultMessage)
    return resultMessage
  }

  private fun configureBrowser() {
    Configuration.browser = "firefox"
    Configuration.browserSize = "1920x1080"
    Configuration.timeout = 10000
    Configuration.headless = true
    Configuration.screenshots = true
    Configuration.savePageSource = true
    Configuration.fastSetValue = true
  }
}
