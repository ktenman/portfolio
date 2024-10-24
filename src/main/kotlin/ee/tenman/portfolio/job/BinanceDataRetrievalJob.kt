package ee.tenman.portfolio.job

import ee.tenman.portfolio.binance.BinanceService
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
class BinanceDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val dailyPriceService: DailyPriceService,
  private val transactionRunner: TransactionRunner,
  private val jobExecutionService: JobExecutionService,
  private val binanceService: BinanceService,
  private val clock: Clock
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 */10 * * * *") // Runs every 10 minutes
  fun runJob() {
    log.info("Running Binance data retrieval job")
    jobExecutionService.executeJob(this)
    log.info("Completed Binance data retrieval job")
  }

  override fun execute() {
    log.info("Starting Binance data retrieval job")
    val instruments = instrumentService.getAllInstruments()
      .filter { it.providerName == ProviderName.BINANCE }

    processInstruments(instruments)
    log.info("Completed Binance data retrieval job. Processed ${instruments.size} instruments.")
  }

  override fun getName(): String = "BinanceDataRetrievalJob"

  private fun processInstruments(instruments: List<Instrument>) {
    instruments.forEach { instrument ->
      try {
        log.info("Retrieving data for instrument: ${instrument.symbol}")
        val dailyData = binanceService.getDailyPrices(instrument.symbol)

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

