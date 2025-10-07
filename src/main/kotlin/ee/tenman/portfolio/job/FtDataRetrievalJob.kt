package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.ft.HistoricalPricesService
import ee.tenman.portfolio.scheduler.AdaptiveSchedulingProperties
import ee.tenman.portfolio.scheduler.MarketPhaseDetectionService
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.util.DataProcessingUtil
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

@Component
@ConditionalOnProperty(name = ["scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class FtDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val historicalPricesService: HistoricalPricesService,
  private val dataProcessingUtil: DataProcessingUtil,
  private val jobExecutionService: JobExecutionService,
  private val taskScheduler: TaskScheduler,
  private val adaptiveSchedulingProperties: AdaptiveSchedulingProperties,
  private val marketPhaseDetectionService: MarketPhaseDetectionService,
  @Value("\${ft.instruments.parallel.threads:5}") private val instrumentParallelThreads: Int = 5,
  private val clock: Clock = Clock.systemDefaultZone(),
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Volatile
  private var isExecuting = false
  private var executionStartTime: Instant? = null
  private var apiCallCount = 0

  @PostConstruct
  fun initializeAdaptiveScheduling() {
    if (adaptiveSchedulingProperties.enabled) {
      log.info("Initializing adaptive scheduling for FT data retrieval")
      scheduleNextExecution(Duration.ofSeconds(30))
    }
  }

  @Scheduled(cron = "0 0/15 * * * *")
  fun runJob() {
    if (!adaptiveSchedulingProperties.enabled) {
      log.info("Running FT data retrieval job (fixed schedule)")
      jobExecutionService.executeJob(this)
      log.info("Completed FT data retrieval job")
    }
  }

  fun runAdaptiveJob() {
    log.info("Running FT data retrieval job (adaptive schedule)")
    jobExecutionService.executeJob(this)
    log.info("Completed FT data retrieval job")
  }

  override fun execute() {
    if (isExecuting) {
      log.warn("FT data retrieval job is already running, skipping this execution")
      return
    }

    isExecuting = true
    executionStartTime = Instant.now(clock)
    apiCallCount = 0

    try {
      log.info("Starting FT data retrieval execution")
      val instruments =
        instrumentService
          .getAllInstruments()
          .filter { it.providerName == ProviderName.FT }

      if (instruments.isEmpty()) {
        log.info("No FT instruments found to process")
        if (adaptiveSchedulingProperties.enabled) {
          scheduleNextExecution(Duration.ofMinutes(5))
        }
        return
      }

      runBlocking {
        val chunkSize = ceil(instruments.size.toDouble() / instrumentParallelThreads).toInt()
        instruments
          .chunked(chunkSize)
          .map { chunk ->
            async {
              chunk.forEach { instrument ->
                processInstrument(instrument)
              }
            }
          }.awaitAll()
      }

      val executionDuration = Duration.between(executionStartTime, Instant.now(clock))
      val apiCallRate = if (executionDuration.seconds > 0) {
        apiCallCount.toDouble() / executionDuration.seconds
      } else {
        apiCallCount.toDouble()
      }

      log.info(
        "Completed FT data retrieval execution. " +
          "Processed ${instruments.size} instruments, " +
          "Made $apiCallCount API calls in ${executionDuration.seconds}s " +
          "(${String.format("%.2f", apiCallRate)} calls/sec)"
      )

      if (adaptiveSchedulingProperties.enabled) {
        scheduleNextExecution()
      }
    } finally {
      isExecuting = false
    }
  }

  private fun processInstrument(instrument: ee.tenman.portfolio.domain.Instrument) {
    try {
      log.info("Retrieving FT data for instrument: ${instrument.symbol}")
      val ftData = historicalPricesService.fetchPrices(instrument.symbol)
      apiCallCount += estimateApiCallsForSymbol(instrument.symbol)

      if (ftData.isEmpty()) {
        log.warn("No FT data found for instrument: ${instrument.symbol}")
        return
      }

      dataProcessingUtil.processDailyData(instrument, ftData, ProviderName.FT)
    } catch (e: Exception) {
      log.error("Error processing instrument ${instrument.symbol}", e)
    }
  }

  private fun estimateApiCallsForSymbol(symbol: String): Int {
    val currentYear = Instant.now(clock).atZone(java.time.ZoneId.systemDefault()).year
    return maxOf(1, currentYear - 2015)
  }

  private fun scheduleNextExecution(customDelay: Duration? = null) {
    val delay = customDelay ?: run {
      val marketPhase = marketPhaseDetectionService.detectMarketPhase()
      val nextInterval = maxOf(
        marketPhase.defaultIntervalSeconds,
        adaptiveSchedulingProperties.minimumIntervalSeconds
      )
      Duration.ofSeconds(nextInterval)
    }

    taskScheduler.schedule(
      { runAdaptiveJob() },
      Instant.now(clock).plus(delay)
    )

    val marketPhase = marketPhaseDetectionService.detectMarketPhase()
    log.info(
      "Scheduled next execution in ${delay.seconds}s " +
        "(Market phase: $marketPhase, " +
        "Phase default: ${marketPhase.defaultIntervalSeconds}s, " +
        "Configured minimum: ${adaptiveSchedulingProperties.minimumIntervalSeconds}s)"
    )
  }
}
