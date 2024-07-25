package ee.tenman.portfolio.job

import ee.tenman.portfolio.alphavantage.AlphaVantageService
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.DailyPriceService
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.JobExecutionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class InstrumentDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val alphaVantageService: AlphaVantageService,
  private val dailyPriceService: DailyPriceService,
  private val transactionRunner: TransactionRunner,
  private val jobExecutionService: JobExecutionService
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 45 0/4 * * *")
  fun runJob() {
    log.info("Running instrument data retrieval job")
    jobExecutionService.executeJob(this)
    log.info("Completed instrument data retrieval job")
  }

  override fun execute() {
    log.info("Starting instrument data retrieval job")
    val instruments = instrumentService.getAllInstruments()

    instruments.forEach { instrument ->
      try {
        log.info("Retrieving data for instrument: ${instrument.symbol}")
        val dailyData = alphaVantageService.getDailyTimeSeriesForLastWeek(instrument.symbol)

        transactionRunner.runInTransaction {
          dailyData.forEach { (date, data) ->
            val dailyPrice = DailyPrice(
              instrument = instrument,
              entryDate = date,
              providerName = ProviderName.ALPHA_VANTAGE,
              openPrice = data.open,
              highPrice = data.high,
              lowPrice = data.low,
              closePrice = data.close,
              volume = data.volume
            )
            dailyPriceService.saveDailyPrice(dailyPrice)
          }
        }

        log.info("Successfully retrieved and processed data for ${instrument.symbol}")
      } catch (e: Exception) {
        log.error("Error retrieving data for instrument ${instrument.symbol}", e)
      }
    }

    log.info("Completed instrument data retrieval job. Processed ${instruments.size} instruments.")
  }

  override fun getName(): String = "InstrumentDataRetrievalJob"
}
