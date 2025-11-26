package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.exception.EntityNotFoundException
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
import java.time.LocalDate

private const val SCALE = 10
private val MIN_DATE: LocalDate = LocalDate.of(2000, 1, 1)

data class ProfitAccumulator(
  val quantity: BigDecimal = BigDecimal.ZERO,
  val cost: BigDecimal = BigDecimal.ZERO,
)

@Service
class TransactionService(
  private val repository: PortfolioTransactionRepository,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(readOnly = true)
  @Cacheable(value = [TRANSACTION_CACHE], key = "#id")
  fun getTransaction(id: Long): PortfolioTransaction =
    repository.findById(id).orElseThrow { EntityNotFoundException("Transaction not found with id: $id") }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @Retryable(value = [ObjectOptimisticLockingFailureException::class], maxAttempts = 5, backoff = Backoff(delay = 100))
  @Caching(
    evict = [
      CacheEvict(value = [TRANSACTION_CACHE], key = "#transaction.id", condition = "#transaction.id != null"),
      CacheEvict(value = [TRANSACTION_CACHE], key = "'transactions'"),
    ],
  )
  fun saveTransaction(transaction: PortfolioTransaction): PortfolioTransaction {
    val saved = repository.save(transaction)
    val related = repository.findAllByInstrumentIdAndPlatformOrderByTransactionDate(saved.instrument.id, saved.platform)
    calculateProfits(related)
    return repository.saveAll(related).find { it.id == saved.id }!!
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @Retryable(value = [ObjectOptimisticLockingFailureException::class], maxAttempts = 5, backoff = Backoff(delay = 100))
  @Caching(evict = [CacheEvict(value = [TRANSACTION_CACHE], key = "#id"), CacheEvict(value = [TRANSACTION_CACHE], key = "'transactions'")])
  fun deleteTransaction(id: Long) = repository.deleteById(id)

  @Transactional(readOnly = true)
  @Cacheable(value = [TRANSACTION_CACHE], key = "'transactions'", unless = "#result.isEmpty()")
  fun getAllTransactions(): List<PortfolioTransaction> = repository.findAllWithInstruments()

  @Transactional(readOnly = true)
  @Cacheable(
    value = [TRANSACTION_CACHE],
    key = "#platforms == null or #platforms.isEmpty() ? 'transactions' : 'transactions:' + #platforms",
    unless = "#result.isEmpty()",
  )
  fun getAllTransactions(platforms: List<String>?): List<PortfolioTransaction> {
    if (platforms.isNullOrEmpty()) return getAllTransactions()
    val enums = parse(platforms)
    if (enums.isEmpty()) return emptyList()
    return repository.findAllByPlatformsWithInstruments(enums)
  }

  @Transactional(readOnly = true)
  @Cacheable(
    value = [TRANSACTION_CACHE],
    key = "'transactions:' + (#platforms?.toString() ?: 'all') + ':' + " +
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
    if (!hasPlatforms && !hasDates) return getAllTransactions()
    val enums = platforms?.let { parse(it) }
    if (hasPlatforms && enums.isNullOrEmpty()) return emptyList()
    val from = fromDate ?: MIN_DATE
    val until = untilDate ?: LocalDate.now().plusYears(100)
    return when {
      !enums.isNullOrEmpty() && hasDates -> repository.findAllByPlatformsAndDateRangeWithInstruments(enums, from, until)
      !enums.isNullOrEmpty() -> repository.findAllByPlatformsWithInstruments(enums)
      hasDates -> repository.findAllByDateRangeWithInstruments(from, until)
      else -> getAllTransactions()
    }
  }

  @Transactional(readOnly = true)
  fun getTransactionHistory(
    filtered: List<PortfolioTransaction>,
    platforms: List<String>?,
  ): List<PortfolioTransaction> {
    if (filtered.isEmpty()) return emptyList()
    val ids = filtered.map { it.instrument.id }.distinct()
    val enums = platforms?.let { parse(it) }
    return if (!enums.isNullOrEmpty()) repository.findAllByPlatformsAndInstrumentIds(enums, ids) else repository.findAllByInstrumentIds(ids)
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
  @Retryable(value = [ObjectOptimisticLockingFailureException::class], maxAttempts = 5, backoff = Backoff(delay = 100))
  fun calculateProfits(
    transactions: List<PortfolioTransaction>,
    price: BigDecimal = BigDecimal.ZERO,
  ) {
    transactions.groupBy { it.platform to it.instrument.id }.values.forEach { group ->
      profit(group.sortedWith(compareBy({ it.transactionDate }, { it.id })), price)
    }
  }

  private fun parse(platforms: List<String>): List<Platform> =
    platforms.mapNotNull { name ->
      runCatching { Platform.valueOf(name) }
        .onFailure { log.warn("Invalid platform name: $name") }
        .getOrNull()
    }

  private fun profit(
    transactions: List<PortfolioTransaction>,
    passed: BigDecimal = BigDecimal.ZERO,
  ) {
    val sorted = transactions.sortedWith(compareBy({ it.transactionDate }, { it.id }))
    val state = sorted.fold(ProfitAccumulator()) { acc, tx -> process(tx, acc) }
    val price = if (passed > BigDecimal.ZERO) passed else sorted.firstOrNull()?.instrument?.currentPrice ?: BigDecimal.ZERO
    distribute(sorted.filter { it.transactionType == TransactionType.BUY }, state.quantity, price)
  }

  private fun process(
    tx: PortfolioTransaction,
    state: ProfitAccumulator,
  ): ProfitAccumulator =
    when (tx.transactionType) {
      TransactionType.BUY -> buy(tx, state)
      TransactionType.SELL -> sell(tx, state)
    }

  private fun buy(
    tx: PortfolioTransaction,
    state: ProfitAccumulator,
  ): ProfitAccumulator {
    val cost = tx.price.multiply(tx.quantity).add(tx.commission)
    tx.realizedProfit = BigDecimal.ZERO
    return ProfitAccumulator(state.quantity.add(tx.quantity), state.cost.add(cost))
  }

  private fun sell(
    tx: PortfolioTransaction,
    state: ProfitAccumulator,
  ): ProfitAccumulator {
    val average = divide(state.cost, state.quantity)
    tx.averageCost = average
    tx.realizedProfit = tx.quantity.multiply(tx.price.subtract(average)).subtract(tx.commission)
    tx.unrealizedProfit = BigDecimal.ZERO
    tx.remainingQuantity = BigDecimal.ZERO
    if (state.quantity <= BigDecimal.ZERO) return state
    val ratio = tx.quantity.divide(state.quantity, SCALE, RoundingMode.HALF_UP)
    return ProfitAccumulator(state.quantity.subtract(tx.quantity), state.cost.multiply(BigDecimal.ONE.subtract(ratio)))
  }

  private fun distribute(
    buys: List<PortfolioTransaction>,
    quantity: BigDecimal,
    price: BigDecimal,
  ) {
    if (quantity <= BigDecimal.ZERO) {
      buys.forEach {
        it.remainingQuantity = BigDecimal.ZERO
        it.unrealizedProfit = BigDecimal.ZERO
        it.averageCost = it.price
      }
      return
    }
    val total = buys.sumOf { it.quantity }
    buys.forEach { tx ->
      val proportional = tx.quantity.multiply(quantity).divide(total, SCALE, RoundingMode.HALF_UP)
      tx.remainingQuantity = proportional
      tx.averageCost = tx.price
      tx.unrealizedProfit = if (price <= BigDecimal.ZERO) BigDecimal.ZERO else proportional.multiply(price.subtract(tx.price))
    }
  }

  private fun divide(
    numerator: BigDecimal,
    denominator: BigDecimal,
  ): BigDecimal = if (denominator > BigDecimal.ZERO) numerator.divide(denominator, SCALE, RoundingMode.HALF_UP) else BigDecimal.ZERO
}
