package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant

@ScheduledJob
class TerUpdateJob(
  private val jobTransactionService: JobTransactionService,
  private val instrumentRepository: InstrumentRepository,
  private val lightyearPriceService: LightyearPriceService,
  private val instrumentService: InstrumentService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 0 3 * * SUN")
  fun runJob() {
    log.info("Running TER update job")
    val startTime = Instant.now()
    var status = JobStatus.SUCCESS
    var message: String? = null
    try {
      message = updateAllTers()
      log.info("Completed TER update job successfully")
    } catch (e: Exception) {
      status = JobStatus.FAILURE
      message = "Failed to update TERs: ${e.message}"
      log.error("TER update job failed", e)
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
    updateAllTers()
  }

  private fun updateAllTers(): String {
    val lightyearInstruments = instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR)
    log.info("Found {} Lightyear instruments to update TERs", lightyearInstruments.size)
    var updated = 0
    var noData = 0
    lightyearInstruments.forEach { instrument ->
      val ter = lightyearPriceService.fetchFundInfo(instrument.symbol)
      if (ter != null) {
        instrumentService.updateTer(instrument.id, ter)
        updated++
        log.debug("Updated TER for {}: {}", instrument.symbol, ter)
      } else {
        noData++
        log.debug("No TER data for {}", instrument.symbol)
      }
    }
    val resultMessage = "Updated $updated/${lightyearInstruments.size} TERs, $noData with no data"
    log.info(resultMessage)
    return resultMessage
  }
}
