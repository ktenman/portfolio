package ee.tenman.portfolio.job

import ee.tenman.portfolio.alphavantage.AlphaVantageService
import ee.tenman.portfolio.binance.BinanceService
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.DailyPriceService
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.JobExecutionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Component
class InstrumentDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val alphaVantageService: AlphaVantageService,
  private val dailyPriceService: DailyPriceService,
  private val transactionRunner: TransactionRunner,
  private val jobExecutionService: JobExecutionService,
  private val binanceService: BinanceService,
  private val clock: Clock
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 0 * * * *")
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
        val dailyData = when (instrument.providerName) {
          ProviderName.ALPHA_VANTAGE -> alphaVantageService.getDailyTimeSeriesForLastWeek(instrument.symbol)
          ProviderName.BINANCE -> binanceService.getDailyPrices(instrument.symbol)
          else -> throw IllegalArgumentException("Unsupported provider: ${instrument.providerName}")
        }

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

        log.info("Successfully retrieved and processed data for ${instrument.symbol}")
      } catch (e: Exception) {
        log.error("Error retrieving data for instrument ${instrument.symbol}", e)
      } finally {
        val currentDate = LocalDate.now(clock)
        val currentInstant = currentDate.atStartOfDay(clock.zone).toInstant()
        val closePrice = dailyPriceService.findLastDailyPrice(instrument, currentDate)?.closePrice
        if (instrument.currentPrice == null) {
          instrument.currentPrice = closePrice
        }
        if (instrument.updatedAt.isBefore(currentInstant.minus(1, ChronoUnit.DAYS))) {
          instrument.currentPrice = closePrice
        }
        instrumentService.saveInstrument(instrument)
      }
    }

    log.info("Completed instrument data retrieval job. Processed ${instruments.size} instruments.")
  }

  override fun getName(): String = "InstrumentDataRetrievalJob"
}
