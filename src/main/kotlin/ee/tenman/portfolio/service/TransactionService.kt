package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
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
import java.math.RoundingMode

@Service
class TransactionService(
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
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
  fun saveTransaction(transaction: PortfolioTransaction): PortfolioTransaction {
    try {
      val saved = portfolioTransactionRepository.save(transaction)
      val relatedTransactions =
        portfolioTransactionRepository
          .findAllByInstrumentIdAndPlatformOrderByTransactionDate(saved.instrument.id, saved.platform)
      calculateTransactionProfits(relatedTransactions)
      return portfolioTransactionRepository
        .saveAll(relatedTransactions)
        .find { it.id == saved.id }!!
    } catch (e: ObjectOptimisticLockingFailureException) {
      log.warn("Optimistic locking failure while saving transaction. Will retry.", e)
      throw e
    }
  }

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
  fun deleteTransaction(id: Long) {
    try {
      portfolioTransactionRepository.deleteById(id)
    } catch (e: ObjectOptimisticLockingFailureException) {
      log.warn("Optimistic locking failure while deleting transaction. Will retry.", e)
      throw e
    }
  }

  @Transactional(readOnly = true)
  @Cacheable(value = [TRANSACTION_CACHE], key = "'transactions'", unless = "#result.isEmpty()")
  fun getAllTransactions(): List<PortfolioTransaction> = portfolioTransactionRepository.findAllWithInstruments()

  @Transactional(readOnly = true)
  @Cacheable(
    value = [TRANSACTION_CACHE],
    key = "#platforms == null or #platforms.isEmpty() ? 'transactions' : 'transactions:' + #platforms",
    unless = "#result.isEmpty()",
  )
  fun getAllTransactions(platforms: List<String>?): List<PortfolioTransaction> {
    if (platforms.isNullOrEmpty()) {
      return getAllTransactions()
    }

    val platformEnums =
      platforms.mapNotNull { platformName ->
        try {
          ee.tenman.portfolio.domain.Platform
            .valueOf(platformName)
        } catch (e: IllegalArgumentException) {
          log.warn("Invalid platform name: $platformName")
          null
        }
      }

    if (platformEnums.isEmpty()) {
      return emptyList()
    }

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

    if (!hasPlatforms && !hasDates) {
      return getAllTransactions()
    }

    val platformEnums =
      platforms?.mapNotNull { platformName ->
        try {
          ee.tenman.portfolio.domain.Platform
            .valueOf(platformName)
        } catch (e: IllegalArgumentException) {
          log.warn("Invalid platform name: $platformName")
          null
        }
      }

    if (hasPlatforms && platformEnums.isNullOrEmpty()) {
      return emptyList()
    }

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
      else ->
        getAllTransactions()
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
  ) {
    try {
      transactions
        .groupBy { it.platform to it.instrument.id }
        .forEach { (_, platformTransactions) ->
          calculateProfitsForPlatform(platformTransactions.sortedWith(compareBy({ it.transactionDate }, { it.id })), currentPrice)
        }
    } catch (e: ObjectOptimisticLockingFailureException) {
      log.warn("Optimistic locking failure while calculating profits. Will retry.", e)
      throw e
    }
  }

  private fun calculateProfitsForPlatform(
    transactions: List<PortfolioTransaction>,
    passedPrice: BigDecimal = BigDecimal.ZERO,
  ) {
    val sortedTransactions = transactions.sortedWith(compareBy({ it.transactionDate }, { it.id }))
    var currentQuantity = BigDecimal.ZERO
    var totalCost = BigDecimal.ZERO

    sortedTransactions.forEach { transaction ->
      when (transaction.transactionType) {
        TransactionType.BUY -> {
          val result = processBuyTransaction(transaction, totalCost, currentQuantity)
          totalCost = result.first
          currentQuantity = result.second
        }
        TransactionType.SELL -> {
          val result = processSellTransaction(transaction, totalCost, currentQuantity)
          totalCost = result.first
          currentQuantity = result.second
        }
      }
    }

    val currentPrice =
      if (passedPrice >
      BigDecimal.ZERO
      ) {
        passedPrice
      } else {
        (sortedTransactions.firstOrNull()?.instrument?.currentPrice ?: BigDecimal.ZERO)
      }

    distributeUnrealizedProfits(sortedTransactions, currentQuantity, currentPrice)
  }

  private fun processBuyTransaction(
    transaction: PortfolioTransaction,
    totalCost: BigDecimal,
    currentQuantity: BigDecimal,
  ): Pair<BigDecimal, BigDecimal> {
    val cost = transaction.price.multiply(transaction.quantity).add(transaction.commission)
    transaction.realizedProfit = BigDecimal.ZERO

    return Pair(
      totalCost.add(cost),
      currentQuantity.add(transaction.quantity),
    )
  }

  private fun processSellTransaction(
    transaction: PortfolioTransaction,
    totalCost: BigDecimal,
    currentQuantity: BigDecimal,
  ): Pair<BigDecimal, BigDecimal> {
    val averageCost = calculateAverageCost(totalCost, currentQuantity)
    transaction.averageCost = averageCost

    val grossProfit =
      calculateSimpleProfit(
        quantity = transaction.quantity,
        buyPrice = averageCost,
        currentPrice = transaction.price,
      )

    transaction.realizedProfit = grossProfit.subtract(transaction.commission)
    transaction.unrealizedProfit = BigDecimal.ZERO
    transaction.remainingQuantity = BigDecimal.ZERO

    if (currentQuantity <= BigDecimal.ZERO) {
      return Pair(totalCost, currentQuantity)
    }

    val sellRatio = transaction.quantity.divide(currentQuantity, 10, RoundingMode.HALF_UP)
    val newTotalCost = totalCost.multiply(BigDecimal.ONE.subtract(sellRatio))
    val newQuantity = currentQuantity.subtract(transaction.quantity)

    return Pair(newTotalCost, newQuantity)
  }

  private fun calculateAverageCost(
    totalCost: BigDecimal,
    currentQuantity: BigDecimal,
  ): BigDecimal =
    if (currentQuantity > BigDecimal.ZERO) {
      totalCost.divide(currentQuantity, 10, RoundingMode.HALF_UP)
    } else {
      BigDecimal.ZERO
    }

  private fun distributeUnrealizedProfits(
    transactions: List<PortfolioTransaction>,
    currentQuantity: BigDecimal,
    currentPrice: BigDecimal,
  ) {
    val buyTransactions = transactions.filter { it.transactionType == TransactionType.BUY }

    if (currentQuantity <= BigDecimal.ZERO) {
      buyTransactions.forEach { it.setZeroUnrealizedMetrics() }
      return
    }

    val totalBuyQuantity = buyTransactions.sumOf { it.quantity }

    buyTransactions.forEach { buyTx ->
      val proportionalQuantity =
        buyTx.quantity
          .multiply(currentQuantity)
          .divide(totalBuyQuantity, 10, RoundingMode.HALF_UP)

      buyTx.remainingQuantity = proportionalQuantity
      buyTx.averageCost = buyTx.price
      buyTx.unrealizedProfit =
        if (currentPrice <= BigDecimal.ZERO) {
          BigDecimal.ZERO
        } else {
          calculateSimpleProfit(
            quantity = proportionalQuantity,
            buyPrice = buyTx.price,
            currentPrice = currentPrice,
          )
        }
    }
  }

  private fun PortfolioTransaction.setZeroUnrealizedMetrics() {
    this.remainingQuantity = BigDecimal.ZERO
    this.unrealizedProfit = BigDecimal.ZERO
    this.averageCost = this.price
  }

  private fun calculateSimpleProfit(
    quantity: BigDecimal,
    buyPrice: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal = quantity.multiply(currentPrice.subtract(buyPrice))

  @Transactional(readOnly = true)
  fun getFullTransactionHistoryForProfitCalculation(
    filteredTransactions: List<PortfolioTransaction>,
    platforms: List<String>?,
  ): List<PortfolioTransaction> {
    if (filteredTransactions.isEmpty()) return emptyList()

    val instrumentIds = filteredTransactions.map { it.instrument.id }.distinct()

    val platformEnums =
      platforms?.mapNotNull { platformName ->
        try {
          ee.tenman.portfolio.domain.Platform
            .valueOf(platformName)
        } catch (e: IllegalArgumentException) {
          null
        }
      }

    return if (!platformEnums.isNullOrEmpty()) {
      portfolioTransactionRepository.findAllByPlatformsAndInstrumentIds(platformEnums, instrumentIds)
    } else {
      portfolioTransactionRepository.findAllByInstrumentIds(instrumentIds)
    }
  }
}
