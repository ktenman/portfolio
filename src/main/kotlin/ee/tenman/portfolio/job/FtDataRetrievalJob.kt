package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.ft.HistoricalPricesService
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.util.DataProcessingUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlin.math.ceil

@Component
class FtDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val historicalPricesService: HistoricalPricesService,
  private val dataProcessingUtil: DataProcessingUtil,
  private val jobExecutionService: JobExecutionService,
  @Value("\${ft.instruments.parallel.threads:5}") private val instrumentParallelThreads: Int = 5,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 0/10 * * * *")
  fun runJob() {
    log.info("Running FT data retrieval job")
    jobExecutionService.executeJob(this)
    log.info("Completed FT data retrieval job")
  }

  override fun execute() {
    log.info("Starting FT data retrieval execution")
    val instruments =
      instrumentService
        .getAllInstruments()
        .filter { it.providerName == ProviderName.FT }

    if (instruments.isEmpty()) {
      log.info("No FT instruments found to process")
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

    log.info("Completed FT data retrieval execution. Processed ${instruments.size} instruments.")
  }

  private fun processInstrument(instrument: ee.tenman.portfolio.domain.Instrument) {
    try {
      log.info("Retrieving FT data for instrument: ${instrument.symbol}")
      val ftData = historicalPricesService.fetchPrices(instrument.symbol)

      if (ftData.isEmpty()) {
        log.warn("No FT data found for instrument: ${instrument.symbol}")
        return
      }

      dataProcessingUtil.processDailyData(instrument, ftData, ProviderName.FT)
    } catch (e: Exception) {
      log.error("Error processing instrument ${instrument.symbol}", e)
    }
  }
}
