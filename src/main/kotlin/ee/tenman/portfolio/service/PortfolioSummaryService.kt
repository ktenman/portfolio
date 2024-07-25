package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import ee.tenman.portfolio.service.xirr.XirrService
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

@Service
class PortfolioSummaryService(
  private val portfolioDailySummaryRepository: PortfolioDailySummaryRepository,
  private val portfolioTransactionService: PortfolioTransactionService,
  private val dailyPriceService: DailyPriceService,
  private val clock: Clock,
  private val xirrService: XirrService,
  private val instrumentService: InstrumentService
) {

  @Transactional(readOnly = true)
  @Cacheable(value = [SUMMARY_CACHE], key = "'summaries'", unless = "#result.isEmpty()")
  fun getAllDailySummaries(): List<PortfolioDailySummary> = portfolioDailySummaryRepository.findAll()

  @Transactional(readOnly = true)
  fun getCurrentDaySummary(): PortfolioDailySummary {
    val currentDate = LocalDate.now(clock)
    val transactions = portfolioTransactionService.getAllTransactions()
      .filter { !it.transactionDate.isAfter(currentDate) }

    val (totalInvestment, currentValue) = processTransactions(transactions, currentDate)
    val xirrResult = calculateXirr(transactions, currentValue, currentDate)

    return PortfolioDailySummary(
      entryDate = currentDate,
      totalValue = currentValue.setScale(4, RoundingMode.HALF_UP),
      xirrAnnualReturn = BigDecimal(xirrResult).setScale(8, RoundingMode.HALF_UP),
      totalProfit = currentValue.subtract(totalInvestment).setScale(4, RoundingMode.HALF_UP),
      earningsPerDay = currentValue.multiply(BigDecimal(xirrResult))
        .divide(BigDecimal(365.25), 4, RoundingMode.HALF_UP)
    )
  }

  private fun processTransactions(
    transactions: List<PortfolioTransaction>,
    latestDate: LocalDate
  ): Pair<BigDecimal, BigDecimal> {
    val (totalInvestment, holdings) = xirrService.calculateInvestmentAndHoldings(transactions)
    val currentValue = calculateCurrentValue(holdings, latestDate)
    return Pair(totalInvestment, currentValue)
  }

  private fun calculateCurrentValue(holdings: Map<Instrument, BigDecimal>, latestDate: LocalDate): BigDecimal {
    return holdings.entries.sumOf { (instrument, quantity) ->
      val price = instrumentService.findInstrument(instrument.id).currentPrice
        ?: dailyPriceService.findLastDailyPrice(instrument, latestDate)?.closePrice
        ?: throw IllegalStateException("No price found for instrument: ${instrument.symbol} on or before $latestDate")
      quantity * price
    }
  }

  private fun calculateXirr(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    date: LocalDate
  ): Double {
    val xirrTransactions = transactions.map { transaction ->
      Transaction(-transaction.price.multiply(transaction.quantity).toDouble(), transaction.transactionDate)
    }
    val finalTransaction = Transaction(currentValue.toDouble(), date)
    return Xirr(xirrTransactions + finalTransaction).calculate()
  }

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [SUMMARY_CACHE], key = "'summaries'")
    ]
  )
  fun saveDailySummaries(summaries: List<PortfolioDailySummary>) {
    val existingSummaries = portfolioDailySummaryRepository.findAllByEntryDateIn(
      summaries.map { it.entryDate }
    ).associateBy { it.entryDate }

    val updatedSummaries = summaries.map { newSummary ->
      existingSummaries[newSummary.entryDate]?.apply {
        totalValue = newSummary.totalValue
        xirrAnnualReturn = newSummary.xirrAnnualReturn
        totalProfit = newSummary.totalProfit
        earningsPerDay = newSummary.earningsPerDay
      } ?: newSummary
    }

    portfolioDailySummaryRepository.saveAll(updatedSummaries)
  }

  @Transactional(readOnly = true)
  fun getDailySummary(date: LocalDate): PortfolioDailySummary? {
    return portfolioDailySummaryRepository.findByEntryDate(date)
  }
}
