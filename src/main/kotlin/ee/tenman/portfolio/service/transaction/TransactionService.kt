package ee.tenman.portfolio.service.transaction

import ee.tenman.portfolio.common.orNotFound
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.ProfitCalculationEngine
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@Service
class TransactionService(
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val profitCalculationEngine: ProfitCalculationEngine,
  private val transactionCacheService: TransactionCacheService,
  private val cacheInvalidationService: CacheInvalidationService,
  private val clock: Clock,
) {
  @Transactional(readOnly = true)
  fun getTransactionById(id: Long): PortfolioTransaction = portfolioTransactionRepository.findById(id).orNotFound(id)

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  fun saveTransaction(transaction: PortfolioTransaction): PortfolioTransaction {
    val saved = portfolioTransactionRepository.save(transaction)
    val relatedTransactions =
      portfolioTransactionRepository
        .findAllByInstrumentIdAndPlatformOrderByTransactionDate(saved.instrument.id, saved.platform)
    calculateTransactionProfits(relatedTransactions)
    val updated = portfolioTransactionRepository.saveAll(relatedTransactions)
    cacheInvalidationService.evictAllRelatedCaches(null, null)
    return requireNotNull(updated.find { it.id == saved.id }) { "Transaction not found after save: ${saved.id}" }
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  fun deleteTransaction(id: Long) {
    portfolioTransactionRepository.deleteById(id)
    cacheInvalidationService.evictAllRelatedCaches(null, null)
  }

  fun getDistinctPlatforms(): List<Platform> = transactionCacheService.getDistinctPlatforms()

  fun coversEveryPlatform(platforms: List<Platform>): Boolean = platforms.containsAll(transactionCacheService.getDistinctPlatforms())

  @Transactional(readOnly = true)
  fun getAllTransactions(): List<PortfolioTransaction> = transactionCacheService.getAllTransactions()

  fun getAllTransactions(platforms: List<String>?): List<PortfolioTransaction> {
    if (platforms.isNullOrEmpty()) return transactionCacheService.getAllTransactions()
    val platformEnums = platforms.mapNotNull { Platform.fromStringOrNull(it) }
    if (platformEnums.isEmpty()) return emptyList()
    return transactionCacheService.getAllTransactions().filter { it.platform in platformEnums }
  }

  @Transactional(readOnly = true)
  @Cacheable(
    value = [TRANSACTION_CACHE],
    key =
      "'transactions:' + (#platforms?.toString() ?: 'all') + ':' + " +
        "(#fromDate?.toString() ?: 'null') + ':' + (#untilDate?.toString() ?: 'null')",
    unless = "#result.isEmpty()",
  )
  fun getAllTransactions(
    platforms: List<String>?,
    fromDate: LocalDate?,
    untilDate: LocalDate?,
  ): List<PortfolioTransaction> {
    val hasPlatforms = !platforms.isNullOrEmpty()
    val hasDates = fromDate != null || untilDate != null
    if (!hasPlatforms && !hasDates) return transactionCacheService.getAllTransactions()
    val platformEnums = platforms?.mapNotNull { Platform.fromStringOrNull(it) }
    if (hasPlatforms && platformEnums.isNullOrEmpty()) return emptyList()
    val effectiveFromDate = fromDate ?: LocalDate.of(2000, 1, 1)
    val effectiveUntilDate = untilDate ?: LocalDate.now(clock).plusYears(100)
    return when {
      !platformEnums.isNullOrEmpty() && hasDates ->
        portfolioTransactionRepository
          .findAllByPlatformsAndDateRangeWithInstruments(platformEnums, effectiveFromDate, effectiveUntilDate)
      !platformEnums.isNullOrEmpty() ->
        portfolioTransactionRepository.findAllByPlatformsWithInstruments(platformEnums)
      hasDates ->
        portfolioTransactionRepository.findAllByDateRangeWithInstruments(effectiveFromDate, effectiveUntilDate)
      else -> transactionCacheService.getAllTransactions()
    }
  }

  fun calculateTransactionProfits(
    transactions: List<PortfolioTransaction>,
    currentPrice: BigDecimal = BigDecimal.ZERO,
  ) = transactions
    .groupBy { it.platform to it.instrument.id }
    .forEach { (_, platformTransactions) ->
      profitCalculationEngine.calculateProfitsForPlatform(platformTransactions, currentPrice)
    }

  @Transactional(readOnly = true)
  fun getFullTransactionHistoryForProfitCalculation(
    filteredTransactions: List<PortfolioTransaction>,
    platforms: List<String>?,
  ): List<PortfolioTransaction> {
    if (filteredTransactions.isEmpty()) return emptyList()
    val instrumentIds = filteredTransactions.map { it.instrument.id }.distinct()
    val platformEnums = platforms?.mapNotNull { Platform.fromStringOrNull(it) }
    if (!platforms.isNullOrEmpty() && platformEnums.isNullOrEmpty()) return emptyList()
    return platformEnums
      ?.takeIf { it.isNotEmpty() }
      ?.let { portfolioTransactionRepository.findAllByPlatformsAndInstrumentIds(it, instrumentIds) }
      ?: portfolioTransactionRepository.findAllByInstrumentIds(instrumentIds)
  }
}
