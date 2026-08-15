package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import java.math.BigDecimal

private val DAYS_PER_MONTH = BigDecimal(365.25 / 12)

fun PortfolioDailySummary.toSummaryDto(profitChange24h: BigDecimal? = null) =
  PortfolioSummaryDto(
    date = entryDate,
    totalValue = totalValue,
    xirrAnnualReturn = xirrAnnualReturn,
    realizedProfit = realizedProfit,
    unrealizedProfit = unrealizedProfit,
    totalProfit = totalProfit,
    earningsPerDay = earningsPerDay,
    earningsPerMonth = earningsPerDay.multiply(DAYS_PER_MONTH),
    totalProfitChange24h = profitChange24h,
  )
