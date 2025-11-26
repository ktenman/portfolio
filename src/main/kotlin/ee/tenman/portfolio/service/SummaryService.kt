package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

private const val SCALE = 10
private val DAYS_PER_YEAR = BigDecimal("365.25")

@Service
class SummaryService(
  private val summaryRepository: PortfolioDailySummaryRepository,
  private val transactionService: TransactionService,
  private val cacheManager: CacheManager,
  private val investmentMetricsService: InvestmentMetricsService,
  private val clock: Clock,
  private val summaryBatchProcessor: SummaryBatchProcessorService,
  private val summaryDeletionService: SummaryDeletionService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  @Caching(evict = [CacheEvict(value = [SUMMARY_CACHE], key = "'summaries'"), CacheEvict(value = [SUMMARY_CACHE], allEntries = true)])
  fun deleteAllDailySummaries() {
    log.info("Deleting all portfolio daily summaries")
    summaryRepository.deleteAll()
  }

  @Caching(evict = [CacheEvict(value = [SUMMARY_CACHE], key = "'summaries'"), CacheEvict(value = [SUMMARY_CACHE], allEntries = true)])
  fun recalculateAllDailySummaries(): Int {
    log.info("Starting full recalculation of portfolio daily summaries")
    cacheManager.getCache(INSTRUMENT_CACHE)?.clear()
    val sorted = transactionService.getAllTransactions().sortedWith(compareBy({ it.transactionDate }, { it.id }))
    if (sorted.isEmpty()) {
      log.info("No transactions found, nothing to recalculate")
      return 0
    }
    val first = sorted.first().transactionDate
    val today = LocalDate.now(clock)
    val yesterday = today.minusDays(1)
    log.info("Recalculating summaries from $first to $yesterday (excluding current day)")
    summaryDeletionService.deleteHistoricalSummaries(today)
    val dates = generateDateRange(first, yesterday)
    val count = summaryBatchProcessor.processSummariesInBatches(dates, ::calculateSummaryForDate)
    log.info("Successfully recalculated and saved $count daily summaries (excluding current day)")
    return count
  }

  @Transactional(readOnly = true)
  @Cacheable(value = [SUMMARY_CACHE], key = "'summaries'", unless = "#result.isEmpty()")
  fun getAllDailySummaries(): List<PortfolioDailySummary> = summaryRepository.findAll()

  @Transactional(readOnly = true)
  @Cacheable(value = [SUMMARY_CACHE], key = "'summaries-page-' + #page + '-size-' + #size", unless = "#result.isEmpty()")
  fun getAllDailySummaries(page: Int, size: Int): Page<PortfolioDailySummary> =
    summaryRepository.findAll(PageRequest.of(page, size, Sort.by("entryDate").descending()))

  @Transactional(readOnly = true)
  fun getCurrentDaySummary(): PortfolioDailySummary = calculateDetails(LocalDate.now(clock))

  @Transactional(readOnly = true)
  fun calculate24hProfitChange(currentSummary: PortfolioDailySummary): BigDecimal? {
    val yesterday = summaryRepository.findByEntryDate(currentSummary.entryDate.minusDays(1)) ?: return null
    return currentSummary.totalProfit.subtract(yesterday.totalProfit)
  }

  fun calculateSummaryForDate(date: LocalDate): PortfolioDailySummary {
    if (isToday(date)) return calculateTodaySummary(date)
    val existing = summaryRepository.findByEntryDate(date)
    if (existing != null) return existing
    return calculateHistoricalSummary(date)
  }

  @Transactional
  @Caching(evict = [CacheEvict(value = [SUMMARY_CACHE], key = "'summaries'"), CacheEvict(value = [SUMMARY_CACHE], allEntries = true)])
  fun saveDailySummary(summary: PortfolioDailySummary): PortfolioDailySummary {
    val existing = summaryRepository.findByEntryDate(summary.entryDate)
    val target = existing?.apply {
      totalValue = summary.totalValue
      xirrAnnualReturn = summary.xirrAnnualReturn
      realizedProfit = summary.realizedProfit
      unrealizedProfit = summary.unrealizedProfit
      totalProfit = summary.totalProfit
      earningsPerDay = summary.earningsPerDay
    } ?: summary
    return summaryRepository.save(target)
  }

  @Transactional
  @Caching(evict = [CacheEvict(value = [SUMMARY_CACHE], key = "'summaries'"), CacheEvict(value = [SUMMARY_CACHE], allEntries = true)])
  fun saveDailySummaries(summaries: List<PortfolioDailySummary>) {
    val existing = summaryRepository.findAllByEntryDateIn(summaries.map { it.entryDate }).associateBy { it.entryDate }
    val merged = summaries.map { s ->
      existing[s.entryDate]?.apply {
        totalValue = s.totalValue
        xirrAnnualReturn = s.xirrAnnualReturn
        realizedProfit = s.realizedProfit
        unrealizedProfit = s.unrealizedProfit
        totalProfit = s.totalProfit
        earningsPerDay = s.earningsPerDay
      } ?: s
    }
    summaryRepository.saveAll(merged)
  }

  @Transactional(readOnly = true)
  fun getDailySummariesBetween(startDate: LocalDate, endDate: LocalDate): List<PortfolioDailySummary> =
    summaryRepository.findAllByEntryDateBetween(startDate, endDate)

  private fun generateDateRange(start: LocalDate, end: LocalDate): List<LocalDate> =
    generateSequence(start) { it.plusDays(1).takeIf { d -> !d.isAfter(end) } }.toList()

  private fun isToday(date: LocalDate): Boolean = date.isEqual(LocalDate.now(clock))

  private fun calculateTodaySummary(date: LocalDate): PortfolioDailySummary {
    val existing = summaryRepository.findByEntryDate(date) ?: return calculateDetails(date)
    val fresh = calculateDetails(date)
    fresh.id = existing.id
    fresh.version = existing.version
    return fresh
  }

  private fun calculateHistoricalSummary(date: LocalDate): PortfolioDailySummary {
    val transactionsOnDate = transactionService.getAllTransactions().filter { it.transactionDate.isEqual(date) }
    if (transactionsOnDate.isEmpty()) {
      val previous = tryPreviousDaySummary(date)
      if (previous != null) return previous
    }
    return calculateDetails(date)
  }

  private fun tryPreviousDaySummary(date: LocalDate): PortfolioDailySummary? {
    val previous = summaryRepository.findByEntryDate(date.minusDays(1)) ?: return null
    val summary = calculateDetails(date)
    if (summary.totalValue.compareTo(previous.totalValue) == 0) return mergeSummary(date, summary)
    return summary
  }

  private fun calculateDetails(date: LocalDate): PortfolioDailySummary {
    val filtered = transactionService.getAllTransactions()
      .filter { !it.transactionDate.isAfter(date) }
      .sortedWith(compareBy({ it.transactionDate }, { it.id }))
    if (filtered.isEmpty()) return createEmptySummary(date)
    val grouped = filtered.groupBy { it.instrument }
    val results = investmentMetricsService.calculatePortfolioMetrics(grouped, date)
    val xirr = investmentMetricsService.adjusted(results.xirrTransactions, date)
    val earnings = results.totalValue.multiply(BigDecimal(xirr)).divide(DAYS_PER_YEAR, SCALE, RoundingMode.HALF_UP)
    return PortfolioDailySummary(
      entryDate = date,
      totalValue = results.totalValue.setScale(SCALE, RoundingMode.HALF_UP),
      xirrAnnualReturn = BigDecimal(xirr).setScale(SCALE, RoundingMode.HALF_UP),
      realizedProfit = results.realizedProfit.setScale(SCALE, RoundingMode.HALF_UP),
      unrealizedProfit = results.unrealizedProfit.setScale(SCALE, RoundingMode.HALF_UP),
      totalProfit = results.totalProfit.setScale(SCALE, RoundingMode.HALF_UP),
      earningsPerDay = earnings.setScale(SCALE, RoundingMode.HALF_UP),
    )
  }

  private fun mergeSummary(date: LocalDate, summary: PortfolioDailySummary): PortfolioDailySummary {
    val existing = summaryRepository.findByEntryDate(date)
    val result = PortfolioDailySummary(
      entryDate = date,
      totalValue = summary.totalValue,
      xirrAnnualReturn = summary.xirrAnnualReturn,
      realizedProfit = summary.realizedProfit,
      unrealizedProfit = summary.unrealizedProfit,
      totalProfit = summary.totalProfit,
      earningsPerDay = summary.earningsPerDay,
    )
    if (existing != null) {
      result.id = existing.id
      result.version = existing.version
    }
    return result
  }

  private fun createEmptySummary(date: LocalDate): PortfolioDailySummary = PortfolioDailySummary(
    entryDate = date,
    totalValue = BigDecimal.ZERO,
    xirrAnnualReturn = BigDecimal.ZERO,
    realizedProfit = BigDecimal.ZERO,
    unrealizedProfit = BigDecimal.ZERO,
    totalProfit = BigDecimal.ZERO,
    earningsPerDay = BigDecimal.ZERO,
  )
}
