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

private const val MAX_POINTS = 300L

@Service
class IntradaySummaryService(
  private val portfolioIntradaySummaryRepository: PortfolioIntradaySummaryRepository,
  private val transactionService: TransactionService,
  private val clock: Clock,
) {
  @Transactional
  fun record(
    summary: PortfolioDailySummary,
    platform: Platform? = null,
  ) {
    portfolioIntradaySummaryRepository.upsert(
      capturedAt = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES),
      platformKey = platform?.name.orEmpty(),
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
    val platformKey = platformKey(platforms) ?: return emptyList()
    val from = Instant.now(clock).minus(days, ChronoUnit.DAYS)
    return portfolioIntradaySummaryRepository
      .findBucketed(from, Duration.ofDays(days).seconds / MAX_POINTS, platformKey)
      .map { it.toIntradayPointDto() }
  }

  private fun platformKey(platforms: List<Platform>?): String? {
    if (platforms == null || transactionService.coversEveryPlatform(platforms)) return ""
    return platforms.singleOrNull()?.name
  }

  @Transactional
  fun deleteOlderThan(cutoff: Instant) {
    portfolioIntradaySummaryRepository.deleteOlderThan(cutoff)
  }
}
