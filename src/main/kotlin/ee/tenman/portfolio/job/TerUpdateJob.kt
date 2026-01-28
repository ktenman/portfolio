package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CompletableFuture

@ScheduledJob
class TerUpdateJob(
  private val jobTransactionService: JobTransactionService,
  private val instrumentRepository: InstrumentRepository,
  private val lightyearPriceService: LightyearPriceService,
  private val instrumentService: InstrumentService,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private val TER_OVERRIDES =
      mapOf(
        "EXUS:GER:EUR" to java.math.BigDecimal("0.15"),
      )
  }

  @PostConstruct
  fun onStartup() {
    CompletableFuture.runAsync {
      log.info("Running TER update job on startup")
      runJob()
    }
  }

  @Scheduled(cron = "0 0 3 * * SUN")
  fun runJob() {
    log.info("Running TER update job")
    val startTime = Instant.now(clock)
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
      val endTime = Instant.now(clock)
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
    log.info("Found ${lightyearInstruments.size} Lightyear instruments to update TERs")
    var updated = 0
    var noData = 0
    lightyearInstruments.forEach { instrument ->
      val ter = TER_OVERRIDES[instrument.symbol] ?: lightyearPriceService.fetchFundInfo(instrument.symbol)
      if (ter != null) {
        instrumentService.updateTer(instrument.id, ter)
        updated++
        log.debug("Updated TER for ${instrument.symbol}: $ter")
      } else {
        noData++
        log.debug("No TER data for ${instrument.symbol}")
      }
    }
    val resultMessage = "Updated $updated/${lightyearInstruments.size} TERs, $noData with no data"
    log.info(resultMessage)
    return resultMessage
  }
}
