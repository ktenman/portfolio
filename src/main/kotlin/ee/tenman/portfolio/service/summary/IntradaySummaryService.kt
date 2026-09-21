package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.dto.IntradaySummaryPointDto
import ee.tenman.portfolio.repository.PortfolioIntradaySummaryRepository
import ee.tenman.portfolio.service.transaction.TransactionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal const val MAX_INTRADAY_POINTS = 300
private const val WHOLE_PORTFOLIO = ""

internal fun bucketSeconds(days: Long) = Duration.ofDays(days).seconds / MAX_INTRADAY_POINTS

@Service
class IntradaySummaryService(
  private val portfolioIntradaySummaryRepository: PortfolioIntradaySummaryRepository,
  private val transactionService: TransactionService,
  private val clock: Clock,
  private val intradaySummaryReplayService: IntradaySummaryReplayService,
) {
  @Transactional
  fun record(
    summary: PortfolioDailySummary,
    platform: Platform? = null,
  ) {
    portfolioIntradaySummaryRepository.upsert(
      capturedAt = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES),
      platformKey = platform?.name ?: WHOLE_PORTFOLIO,
      totalValue = summary.totalValue,
      xirrAnnualReturn = summary.xirrAnnualReturn,
      totalProfit = summary.totalProfit,
      earningsPerDay = summary.earningsPerDay,
    )
  }

  @Transactional(readOnly = true)
  fun getPoints(
    range: TimeRange,
    platforms: List<Platform>?,
  ): List<IntradaySummaryPointDto> {
    val days = range.intradayDays(LocalDate.now(clock)) ?: return emptyList()
    val selection = platforms?.distinct()?.sortedBy { it.name }
    val platformKey = platformKey(selection) ?: return intradaySummaryReplayService.getPoints(days, selection.orEmpty())
    val from = Instant.now(clock).minus(days, ChronoUnit.DAYS)
    return portfolioIntradaySummaryRepository
      .findBucketed(from, bucketSeconds(days), platformKey)
      .map { it.toIntradayPointDto() }
  }

  private fun platformKey(platforms: List<Platform>?): String? {
    if (platforms == null || transactionService.coversEveryPlatform(platforms)) return WHOLE_PORTFOLIO
    return platforms.singleOrNull()?.name
  }

  @Transactional
  fun deleteOlderThan(cutoff: Instant) {
    portfolioIntradaySummaryRepository.deleteOlderThan(cutoff)
  }
}
