package ee.tenman.portfolio.service.transaction

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.ProfitCalculationEngine
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class TransactionService(
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val profitCalculationEngine: ProfitCalculationEngine,
  private val transactionCacheService: TransactionCacheService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(readOnly = true)
  @Cacheable(value = [TRANSACTION_CACHE], key = "#id")
  fun getTransactionById(id: Long): PortfolioTransaction =
    portfolioTransactionRepository
      .findById(id)
      .orElseThrow {
        ee.tenman.portfolio.exception
        .EntityNotFoundException("Transaction not found with id: $id")
      }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @Retryable(
    value = [ObjectOptimisticLockingFailureException::class],
    maxAttempts = 5,
    backoff = Backoff(delay = 100),
  )
  @Caching(
    evict = [
      CacheEvict(value = [TRANSACTION_CACHE], key = "#transaction.id", condition = "#transaction.id != null"),
      CacheEvict(value = [TRANSACTION_CACHE], key = "'transactions'"),
    ],
  )
  fun saveTransaction(transaction: PortfolioTransaction): PortfolioTransaction =
    runCatching {
      val saved = portfolioTransactionRepository.save(transaction)
      val relatedTransactions =
        portfolioTransactionRepository
          .findAllByInstrumentIdAndPlatformOrderByTransactionDate(saved.instrument.id, saved.platform)
      calculateTransactionProfits(relatedTransactions)
      portfolioTransactionRepository
        .saveAll(relatedTransactions)
        .find { it.id == saved.id }!!
    }.onFailure { e ->
      if (e is ObjectOptimisticLockingFailureException) {
        log.warn("Optimistic locking failure while saving transaction. Will retry.", e)
      }
    }.getOrThrow()

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @Retryable(
    value = [ObjectOptimisticLockingFailureException::class],
    maxAttempts = 5,
    backoff = Backoff(delay = 100),
  )
  @Caching(
    evict = [
      CacheEvict(value = [TRANSACTION_CACHE], key = "#id"),
      CacheEvict(value = [TRANSACTION_CACHE], key = "'transactions'"),
    ],
  )
  fun deleteTransaction(id: Long) =
    runCatching {
      portfolioTransactionRepository.deleteById(id)
    }.onFailure { e ->
      if (e is ObjectOptimisticLockingFailureException) {
        log.warn("Optimistic locking failure while deleting transaction. Will retry.", e)
      }
    }.getOrThrow()

  @Transactional(readOnly = true)
  fun getAllTransactions(): List<PortfolioTransaction> = transactionCacheService.getAllTransactions()

  @Transactional(readOnly = true)
  @Cacheable(
    value = [TRANSACTION_CACHE],
    key = "#platforms == null or #platforms.isEmpty() ? 'transactions' : 'transactions:' + #platforms",
    unless = "#result.isEmpty()",
  )
  fun getAllTransactions(platforms: List<String>?): List<PortfolioTransaction> {
    if (platforms.isNullOrEmpty()) return transactionCacheService.getAllTransactions()
    val platformEnums = platforms.mapNotNull { it.toPlatformOrNull() }
    if (platformEnums.isEmpty()) return emptyList()
    return portfolioTransactionRepository.findAllByPlatformsWithInstruments(platformEnums)
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
    fromDate: java.time.LocalDate?,
    untilDate: java.time.LocalDate?,
  ): List<PortfolioTransaction> {
    val hasPlatforms = !platforms.isNullOrEmpty()
    val hasDates = fromDate != null || untilDate != null
    if (!hasPlatforms && !hasDates) return transactionCacheService.getAllTransactions()
    val platformEnums = platforms?.mapNotNull { it.toPlatformOrNull() }
    if (hasPlatforms && platformEnums.isNullOrEmpty()) return emptyList()
    val effectiveFromDate = fromDate ?: java.time.LocalDate.of(2000, 1, 1)
    val effectiveUntilDate =
      untilDate ?: java.time.LocalDate
        .now()
        .plusYears(100)
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

  @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
  @Retryable(
    value = [ObjectOptimisticLockingFailureException::class],
    maxAttempts = 5,
    backoff = Backoff(delay = 100),
  )
  fun calculateTransactionProfits(
    transactions: List<PortfolioTransaction>,
    currentPrice: BigDecimal = BigDecimal.ZERO,
  ) = runCatching {
      transactions
        .groupBy { it.platform to it.instrument.id }
        .forEach { (_, platformTransactions) ->
          profitCalculationEngine.calculateProfitsForPlatform(platformTransactions, currentPrice)
        }
    }.onFailure { e ->
      if (e is ObjectOptimisticLockingFailureException) {
        log.warn("Optimistic locking failure while calculating profits. Will retry.", e)
      }
    }.getOrThrow()

  @Transactional(readOnly = true)
  fun getFullTransactionHistoryForProfitCalculation(
    filteredTransactions: List<PortfolioTransaction>,
    platforms: List<String>?,
  ): List<PortfolioTransaction> {
    if (filteredTransactions.isEmpty()) return emptyList()
    val instrumentIds = filteredTransactions.map { it.instrument.id }.distinct()
    val platformEnums = platforms?.mapNotNull { it.toPlatformOrNull() }
    if (!platforms.isNullOrEmpty() && platformEnums.isNullOrEmpty()) return emptyList()
    return platformEnums
      ?.takeIf { it.isNotEmpty() }
      ?.let { portfolioTransactionRepository.findAllByPlatformsAndInstrumentIds(it, instrumentIds) }
      ?: portfolioTransactionRepository.findAllByInstrumentIds(instrumentIds)
  }

  private fun String.toPlatformOrNull(): Platform? =
    runCatching { Platform.valueOf(this) }
      .onFailure { log.warn("Invalid platform name: {}", this) }
      .getOrNull()
}
