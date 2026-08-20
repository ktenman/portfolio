package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.common.percentOf
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import ee.tenman.portfolio.dto.RangeChangeDto
import ee.tenman.portfolio.service.transaction.TransactionService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

@Service
class PortfolioRangeChangeService(
  private val seriesService: PortfolioSummarySeriesService,
  private val currentDaySummaryCacheService: CurrentDaySummaryCacheService,
  private val platformSummaryCacheService: PlatformSummaryCacheService,
  private val transactionService: TransactionService,
  private val clock: Clock,
) {
  fun calculate(
    range: TimeRange,
    platforms: List<Platform>?,
  ): RangeChangeDto {
    val current = current(platforms)
    val opening = opening(range, platforms)
    val amount = current.totalProfit.subtract(opening?.totalProfit ?: BigDecimal.ZERO)
    val capital = (opening?.totalValue ?: BigDecimal.ZERO).add(contributions(platforms, opening?.date))
    return RangeChangeDto(
      changeAmount = amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
      changePercent = percent(amount, capital),
    )
  }

  private fun current(platforms: List<Platform>?): PortfolioDailySummary {
    if (platforms == null) return currentDaySummaryCacheService.getCurrentDaySummary()
    return platformSummaryCacheService.getCurrentDaySummaryForPlatforms(platforms)
  }

  private fun opening(
    range: TimeRange,
    platforms: List<Platform>?,
  ): PortfolioSummaryDto? {
    val start = range.startDate(LocalDate.now(clock)) ?: return null
    val opening = seriesService.getSeries(range, platforms).firstOrNull() ?: return null
    if (opening.date.isAfter(start)) return null
    return opening
  }

  private fun contributions(
    platforms: List<Platform>?,
    after: LocalDate?,
  ): BigDecimal =
    transactionService
      .getAllTransactions(platforms?.map { it.name }, after?.plusDays(1), null)
      .groupBy { it.transactionDate }
      .values
      .map { sameDay -> sameDay.fold(BigDecimal.ZERO) { total, transaction -> total.add(flow(transaction)) } }
      .filter { it > BigDecimal.ZERO }
      .fold(BigDecimal.ZERO, BigDecimal::add)

  private fun flow(transaction: PortfolioTransaction): BigDecimal {
    val traded = transaction.quantity.multiply(transaction.price)
    if (transaction.transactionType == TransactionType.BUY) return traded.add(transaction.commission)
    return transaction.commission.subtract(traded)
  }

  private fun percent(
    amount: BigDecimal,
    capital: BigDecimal,
  ): BigDecimal {
    if (capital <= BigDecimal.ZERO) return BigDecimal.ZERO
    return amount.percentOf(capital, PERCENT_SCALE)
  }

  companion object {
    private const val AMOUNT_SCALE = 2
    private const val PERCENT_SCALE = 6
  }
}
