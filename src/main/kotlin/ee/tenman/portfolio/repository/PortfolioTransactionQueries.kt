package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.BaseEntity
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

interface PortfolioTransactionQueries {
  fun findAllNewestFirst(): List<PortfolioTransaction>
}

class PortfolioTransactionQueriesImpl(
  private val jdbcClient: JdbcClient,
  private val instrumentRepository: InstrumentRepository,
) : PortfolioTransactionQueries {
  override fun findAllNewestFirst(): List<PortfolioTransaction> {
    val instruments = instrumentRepository.findAll().associateBy { it.id }
    return jdbcClient
      .sql("SELECT * FROM portfolio_transaction ORDER BY transaction_date DESC, id DESC")
      .query { row, _ -> row.toTransaction(instruments.getValue(row.getLong("instrument_id"))) }
      .list()
  }
}

private fun ResultSet.toTransaction(instrument: Instrument): PortfolioTransaction =
  PortfolioTransaction(
    instrument = instrument,
    transactionType = TransactionType.valueOf(getString("transaction_type")),
    quantity = getBigDecimal("quantity"),
    price = getBigDecimal("price"),
    transactionDate = getObject("transaction_date", LocalDate::class.java),
    platform = Platform.valueOf(getString("platform")),
    realizedProfit = getBigDecimal("realized_profit"),
    unrealizedProfit = getBigDecimal("unrealized_profit"),
    averageCost = getBigDecimal("average_cost"),
    remainingQuantity = getBigDecimal("remaining_quantity") ?: BigDecimal.ZERO,
    commission = getBigDecimal("commission"),
    currency = getString("currency"),
  ).restore(this)

private fun <T : BaseEntity> T.restore(row: ResultSet): T =
  apply {
    id = row.getLong("id")
    createdAt = checkNotNull(row.getInstant("created_at"))
    updatedAt = checkNotNull(row.getInstant("updated_at"))
    version = row.getLong("version")
  }

private fun ResultSet.getInstant(column: String): Instant? = getObject(column, OffsetDateTime::class.java)?.toInstant()
