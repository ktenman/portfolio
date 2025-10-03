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
      .orElseThrow { RuntimeException("Transaction not found with id: $id") }

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
  fun getAllTransactions(): List<PortfolioTransaction> {
    val transactions = portfolioTransactionRepository.findAllWithInstruments()
    calculateTransactionProfits(transactions)
    return transactions
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @Retryable(
    value = [ObjectOptimisticLockingFailureException::class],
    maxAttempts = 5,
    backoff = Backoff(delay = 100),
  )
  fun calculateTransactionProfits(transactions: List<PortfolioTransaction>) {
    try {
      transactions
        .groupBy { it.platform to it.instrument.id }
        .forEach { (_, platformTransactions) ->
          calculateProfitsForPlatform(platformTransactions.sortedBy { it.transactionDate })
        }
    } catch (e: ObjectOptimisticLockingFailureException) {
      log.warn("Optimistic locking failure while calculating profits. Will retry.", e)
      throw e
    }
  }

  private fun calculateProfitsForPlatform(transactions: List<PortfolioTransaction>) {
    val sortedTransactions = transactions.sortedBy { it.transactionDate }
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

    val currentPrice = sortedTransactions.firstOrNull()?.instrument?.currentPrice ?: BigDecimal.ZERO
    val averageCost = calculateAverageCost(totalCost, currentQuantity)
    val totalUnrealizedProfit = calculateTotalUnrealizedProfit(currentQuantity, currentPrice, averageCost)

    distributeUnrealizedProfits(sortedTransactions, currentQuantity, averageCost, totalUnrealizedProfit)
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

  private fun calculateTotalUnrealizedProfit(
    currentQuantity: BigDecimal,
    currentPrice: BigDecimal,
    averageCost: BigDecimal,
  ): BigDecimal =
    if (currentQuantity > BigDecimal.ZERO && currentPrice > BigDecimal.ZERO) {
      calculateSimpleProfit(
        quantity = currentQuantity,
        buyPrice = averageCost,
        currentPrice = currentPrice,
      )
    } else {
      BigDecimal.ZERO
    }

  private fun distributeUnrealizedProfits(
    transactions: List<PortfolioTransaction>,
    currentQuantity: BigDecimal,
    averageCost: BigDecimal,
    totalUnrealizedProfit: BigDecimal,
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
      buyTx.averageCost = averageCost
      buyTx.unrealizedProfit =
        totalUnrealizedProfit
          .multiply(proportionalQuantity)
          .divide(currentQuantity, 10, RoundingMode.HALF_UP)
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
}
