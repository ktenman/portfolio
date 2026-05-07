package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.dto.XirrWindowDto
import ee.tenman.portfolio.dto.XirrWindowsDto
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.Period

@Service
class PortfolioXirrWindowService(
  private val summaryRepository: PortfolioDailySummaryRepository,
  private val transactionRepository: PortfolioTransactionRepository,
  private val summaryService: SummaryService,
  private val xirrCalculationService: XirrCalculationService,
  private val clock: Clock,
) {
  private val windows: List<XirrWindowDefinition> =
    listOf(
      XirrWindowDefinition("1M", Period.ofMonths(1)),
      XirrWindowDefinition("3M", Period.ofMonths(3)),
      XirrWindowDefinition("6M", Period.ofMonths(6)),
      XirrWindowDefinition("1Y", Period.ofYears(1)),
      XirrWindowDefinition("2Y", Period.ofYears(2)),
      XirrWindowDefinition("3Y", Period.ofYears(3)),
    )

  @Transactional(readOnly = true)
  fun calculate(platforms: List<Platform>?): XirrWindowsDto {
    val today = LocalDate.now(clock)
    val currentValue = lookupCurrentTotalValue(platforms, today)
    val rows = windows.map { window -> calculateWindow(window, today, currentValue, platforms) }
    return XirrWindowsDto(rows)
  }

  private fun calculateWindow(
    window: XirrWindowDefinition,
    today: LocalDate,
    currentValue: BigDecimal,
    platforms: List<Platform>?,
  ): XirrWindowDto {
    val targetStart = today.minus(window.length)
    val opening = lookupOpening(platforms, targetStart) ?: return notAvailable(window.label)
    if (currentValue <= BigDecimal.ZERO || opening.totalValue <= BigDecimal.ZERO) {
      return notAvailable(window.label)
    }
    val cashFlows = buildCashFlows(opening.entryDate, today, opening.totalValue, currentValue, platforms)
    val xirr = xirrCalculationService.calculateAdjustedXirr(cashFlows, today)
    return XirrWindowDto(
      period = window.label,
      fromDate = opening.entryDate,
      xirr = xirr?.let { BigDecimal(it).setScale(SCALE, RoundingMode.HALF_UP) },
    )
  }

  private fun buildCashFlows(
    fromDate: LocalDate,
    toDate: LocalDate,
    openingValue: BigDecimal,
    closingValue: BigDecimal,
    platforms: List<Platform>?,
  ): List<CashFlow> {
    val transactionsInWindow = lookupTransactions(platforms, fromDate.plusDays(1), toDate)
    val opening = CashFlow(-openingValue.toDouble(), fromDate)
    val realFlows = transactionsInWindow.map(xirrCalculationService::convertToCashFlow)
    val closing = CashFlow(closingValue.toDouble(), toDate)
    return listOf(opening) + realFlows + closing
  }

  private fun lookupCurrentTotalValue(
    platforms: List<Platform>?,
    today: LocalDate,
  ): BigDecimal =
    if (platforms == null) {
      summaryService.getCurrentDaySummary().totalValue
    } else {
      summaryService.getSummaryForPlatformsOnDate(platforms, today).totalValue
    }

  private fun lookupOpening(
    platforms: List<Platform>?,
    targetStart: LocalDate,
  ): XirrWindowOpeningPoint? {
    if (platforms == null) {
      val summary = summaryRepository.findFirstByEntryDateLessThanEqualOrderByEntryDateDesc(targetStart) ?: return null
      return XirrWindowOpeningPoint(summary.entryDate, summary.totalValue)
    }
    val summary = summaryService.getSummaryForPlatformsOnDate(platforms, targetStart)
    if (summary.totalValue <= BigDecimal.ZERO) return null
    return XirrWindowOpeningPoint(targetStart, summary.totalValue)
  }

  private fun lookupTransactions(
    platforms: List<Platform>?,
    fromDate: LocalDate,
    toDate: LocalDate,
  ): List<PortfolioTransaction> {
    if (platforms == null) return transactionRepository.findAllByDateRangeWithInstruments(fromDate, toDate)
    return transactionRepository.findAllByPlatformsAndDateRangeWithInstruments(platforms, fromDate, toDate)
  }

  private fun notAvailable(label: String) = XirrWindowDto(period = label, fromDate = null, xirr = null)

  companion object {
    private const val SCALE = 6
  }
}
