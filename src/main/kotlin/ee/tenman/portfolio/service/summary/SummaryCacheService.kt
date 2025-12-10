package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class SummaryCacheService(
  private val portfolioDailySummaryRepository: PortfolioDailySummaryRepository,
  private val cacheInvalidationService: CacheInvalidationService,
) {
  @Transactional(readOnly = true)
  @Cacheable(value = [SUMMARY_CACHE], key = "'summaries'", unless = "#result.isEmpty()")
  fun getAllDailySummaries(): List<PortfolioDailySummary> = portfolioDailySummaryRepository.findAll()

  @Transactional(readOnly = true)
  @Cacheable(
    value = [SUMMARY_CACHE],
    key = "'summaries-page-' + #page + '-size-' + #size",
    unless = "#result.isEmpty()",
  )
  fun getAllDailySummaries(
    page: Int,
    size: Int,
  ): Page<PortfolioDailySummary> {
    val pageable = PageRequest.of(page, size, Sort.by("entryDate").descending())
    return portfolioDailySummaryRepository.findAll(pageable)
  }

  @Transactional(readOnly = true)
  @Cacheable(
    value = [SUMMARY_CACHE],
    key = "'profit-24h-' + #summary.entryDate.toString()",
    unless = "#result == null",
  )
  fun calculate24hProfitChange(summary: PortfolioDailySummary): BigDecimal? {
    val yesterday = summary.entryDate.minusDays(1)
    val yesterdaySummary = portfolioDailySummaryRepository.findByEntryDate(yesterday) ?: return null
    return summary.totalProfit.subtract(yesterdaySummary.totalProfit)
  }

  @Transactional(readOnly = true)
  @Cacheable(
    value = [SUMMARY_CACHE],
    key = "'summary-' + #date.toString()",
    unless = "#result == null",
  )
  fun findByEntryDate(date: LocalDate): PortfolioDailySummary? = portfolioDailySummaryRepository.findByEntryDate(date)

  fun evictAllCaches() = cacheInvalidationService.evictSummaryCaches()
}
