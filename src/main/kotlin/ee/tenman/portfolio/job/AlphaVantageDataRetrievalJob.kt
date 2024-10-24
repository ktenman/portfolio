package ee.tenman.portfolio.job

import ee.tenman.portfolio.alphavantage.AlphaVantageService
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.DailyPriceService
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.JobExecutionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class AlphaVantageDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val alphaVantageService: AlphaVantageService,
  private val dailyPriceService: DailyPriceService,
  private val transactionRunner: TransactionRunner,
  private val jobExecutionService: JobExecutionService,
  private val clock: Clock
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 0 0/3 * * *") // Runs every 3 hours
  fun runJob() {
    log.info("Running AlphaVantage data retrieval job")
    jobExecutionService.executeJob(this)
    log.info("Completed AlphaVantage data retrieval job")
  }

  override fun execute() {
    log.info("Starting AlphaVantage data retrieval job")
    val instruments = instrumentService.getAllInstruments()
      .filter { it.providerName == ProviderName.ALPHA_VANTAGE }

    processInstruments(instruments)
    log.info("Completed AlphaVantage data retrieval job. Processed ${instruments.size} instruments.")
  }

  override fun getName(): String = "AlphaVantageDataRetrievalJob"

  private fun processInstruments(instruments: List<Instrument>) {
    instruments.forEach { instrument ->
      try {
        log.info("Retrieving data for instrument: ${instrument.symbol}")
        val dailyData = alphaVantageService.getDailyTimeSeriesForLastWeek(instrument.symbol)

        transactionRunner.runInTransaction {
          dailyData.forEach { (date, data) ->
            val dailyPrice = DailyPrice(
              instrument = instrument,
              entryDate = date,
              providerName = instrument.providerName,
              openPrice = data.open,
              highPrice = data.high,
              lowPrice = data.low,
              closePrice = data.close,
              volume = data.volume
            )
            dailyPriceService.saveDailyPrice(dailyPrice)
          }
        }

        updateInstrumentPrice(instrument)
        log.info("Successfully retrieved and processed data for ${instrument.symbol}")
      } catch (e: Exception) {
        log.error("Error retrieving data for instrument ${instrument.symbol}", e)
      }
    }
  }

  private fun updateInstrumentPrice(instrument: Instrument) {
    val currentDate = LocalDate.now(clock)
    val dailyPrice = dailyPriceService.findLastDailyPrice(instrument, currentDate)
    instrument.currentPrice = dailyPrice?.closePrice
    instrumentService.saveInstrument(instrument)
  }
}
