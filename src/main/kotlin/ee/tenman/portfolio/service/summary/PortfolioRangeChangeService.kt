package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.common.percentOf
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.dto.RangeChangeDto
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
  private val clock: Clock,
) {
  fun calculate(
    range: TimeRange,
    platforms: List<Platform>?,
  ): RangeChangeDto {
    val current = current(platforms)
    val amount = current.totalProfit.subtract(baseline(range, platforms))
    return RangeChangeDto(
      changeAmount = amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
      changePercent = percent(amount, current.totalValue),
    )
  }

  private fun current(platforms: List<Platform>?): PortfolioDailySummary {
    if (platforms == null) return currentDaySummaryCacheService.getCurrentDaySummary()
    return platformSummaryCacheService.getCurrentDaySummaryForPlatforms(platforms)
  }

  private fun baseline(
    range: TimeRange,
    platforms: List<Platform>?,
  ): BigDecimal {
    val start = range.startDate(LocalDate.now(clock)) ?: return BigDecimal.ZERO
    val opening = seriesService.getSeries(range, platforms).firstOrNull() ?: return BigDecimal.ZERO
    if (opening.date.isAfter(start)) return BigDecimal.ZERO
    return opening.totalProfit
  }

  private fun percent(
    amount: BigDecimal,
    totalValue: BigDecimal,
  ): BigDecimal {
    if (totalValue <= BigDecimal.ZERO) return BigDecimal.ZERO
    return amount.percentOf(totalValue, PERCENT_SCALE)
  }

  companion object {
    private const val AMOUNT_SCALE = 2
    private const val PERCENT_SCALE = 6
  }
}
