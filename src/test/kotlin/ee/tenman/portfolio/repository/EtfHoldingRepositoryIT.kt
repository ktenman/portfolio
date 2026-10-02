package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.job.TransactionRunner
import jakarta.annotation.Resource
import jakarta.persistence.EntityManager
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.data.jpa.repository.Query
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.util.function.UnaryOperator
import javax.sql.DataSource

@IntegrationTest
class EtfHoldingRepositoryIT {
  @Resource
  private lateinit var repository: EtfHoldingRepository

  @Resource
  private lateinit var instruments: InstrumentRepository

  @Resource
  private lateinit var positions: EtfPositionRepository

  @Resource
  private lateinit var transactions: PortfolioTransactionRepository

  @Resource
  private lateinit var runner: TransactionRunner

  @Resource
  private lateinit var entityManager: EntityManager

  @Resource
  private lateinit var dataSource: DataSource

  @Test
  fun `should find a ticker without scanning unrelated holdings`() {
    runner.runInTransaction {
      execute(
        """
        INSERT INTO etf_holding (uuid, name, ticker)
        SELECT gen_random_uuid(), concat('Õunake ', g), concat('T', g)
        FROM generate_series(1, 8000) g
        """,
      )
      execute("ANALYZE etf_holding")
    }
    val plan = explain("SELECT * FROM etf_holding WHERE ticker = 'T4000'")
    expect(plan.path("Shared Hit Blocks").asLong() + plan.path("Shared Read Blocks").asLong()).toBeLessThan(10L)
  }

  @Test
  fun `should return distinct companies sharing a ticker`() {
    repository.saveAll(listOf(EtfHolding(name = "Merck & Co", ticker = "MRK"), EtfHolding(name = "Merck KGaA", ticker = "MRK")))
    expect(repository.findByTicker("MRK").map { it.name }.sorted()).toContainExactly("Merck & Co", "Merck KGaA")
  }

  @Test
  fun `should select missing logos by net quantity across funds in descending maximum weight order`() {
    val held = fund("HELD")
    val sold = fund("SOLD")
    val short = fund("SHORT")
    val empty = fund("EMPTY")
    trade(held, TransactionType.BUY, "2")
    trade(held, TransactionType.SELL, "1")
    trade(sold, TransactionType.BUY, "2")
    trade(sold, TransactionType.SELL, "2")
    trade(short, TransactionType.SELL, "1")
    position(held, holding("Škoda Auto"), "3")
    position(held, holding("Õunake AS", LogoSource.LIGHTYEAR), "8")
    position(sold, holding("Müüdud Ühistu"), "9")
    position(empty, holding("Ilma Tehinguta"), "20")
    val shared = holding("Jagatud Ühistu")
    position(held, shared, "7")
    position(short, shared, "6")
    val combined = holding("Kombineeritud Osalus")
    position(held, combined, "4")
    position(sold, combined, "11")
    expect(repository.findHoldingsWithoutLogosForCurrentPortfolio().map { it.name }).toContainExactly("Kombineeritud Osalus", "Škoda Auto")
  }

  @ParameterizedTest
  @CsvSource("0.004, 2, false", "0.006, 2, true", "0.01, 1, false", "0.011, 1, true")
  fun `should preserve the quantity threshold across historical snapshots`(
    quantity: String,
    snapshots: Int,
    selected: Boolean,
  ) {
    val fund = fund("HELD")
    val holding = holding("Väike Osalus")
    trade(fund, TransactionType.BUY, quantity)
    repeat(snapshots) { position(fund, holding, "5", LocalDate.of(2026, 9, 1).plusDays(it.toLong())) }
    expect(repository.findHoldingsWithoutLogosForCurrentPortfolio().isNotEmpty()).toEqual(selected)
  }

  @Test
  fun `should avoid multiplying fund positions by the number of transactions when selecting logos`() {
    val fund = fund("HELD")
    runner.runInTransaction {
      insertPositions(fund.id)
      execute(
        """
        INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
        SELECT ${fund.id}, 'BUY', 1, 10, DATE '2026-09-01', 'LIGHTYEAR'
        FROM generate_series(1, 500)
        """,
      )
      execute("ANALYZE etf_position, etf_holding, portfolio_transaction")
    }
    expect(maximumRows(explain(logoSql()))).toBeLessThanOrEqualTo(4000L)
  }

  private fun fund(symbol: String): Instrument =
    instruments.save(Instrument(symbol = symbol, name = "$symbol fond", category = "ETF", baseCurrency = "EUR"))

  private fun holding(
    name: String,
    logo: LogoSource? = null,
  ): EtfHolding = repository.save(EtfHolding(name = name, logoSource = logo))

  private fun position(
    fund: Instrument,
    holding: EtfHolding,
    weight: String,
    date: LocalDate = LocalDate.of(2026, 9, 1),
  ) {
    positions.save(EtfPosition(etfInstrument = fund, holding = holding, snapshotDate = date, weightPercentage = BigDecimal(weight)))
  }

  private fun trade(
    fund: Instrument,
    type: TransactionType,
    quantity: String,
  ) {
    transactions.save(
      PortfolioTransaction(
        instrument = fund,
        transactionType = type,
        quantity = BigDecimal(quantity),
        price = BigDecimal.TEN,
        transactionDate = LocalDate.of(2026, 9, 1),
        platform = Platform.LIGHTYEAR,
      ),
    )
  }

  private fun insertPositions(fund: Long) {
    execute(
      """
      WITH holding AS (
        INSERT INTO etf_holding (uuid, name)
        SELECT gen_random_uuid(), concat('Õunake ', g) FROM generate_series(1, 2000) g
        RETURNING id
      )
      INSERT INTO etf_position (etf_instrument_id, holding_id, snapshot_date, weight_percentage)
      SELECT $fund, id, DATE '2026-09-01', 0.05 FROM holding
      """,
    )
  }

  private fun logoSql(): String {
    var sql = ""
    val hql =
      EtfHoldingRepository::class.java
        .getMethod("findHoldingsWithoutLogosForCurrentPortfolio")
        .getAnnotation(Query::class.java)
        .value
    val factory = entityManager.entityManagerFactory.unwrap(SessionFactory::class.java)
    val inspector =
      UnaryOperator<String> { statement ->
        sql = statement
        statement
      }
    factory.withOptions().statementInspector(inspector).openSession().use { session ->
      session.createSelectionQuery(hql, EtfHolding::class.java).setTimeout(10).resultList
    }
    return sql
  }

  private fun explain(sql: String): JsonNode =
    dataSource.connection.use { connection ->
      connection.createStatement().use { statement ->
        statement.queryTimeout = 10
        statement.executeQuery("EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, FORMAT JSON) $sql").use { rows ->
          check(rows.next()) { "No execution plan returned for $sql" }
          ObjectMapper().readTree(rows.getString(1))[0].path("Plan")
        }
      }
    }

  private fun maximumRows(plan: JsonNode): Long =
    maxOf(
      plan.path("Actual Rows").asLong() * plan.path("Actual Loops").asLong(),
      plan.path("Plans").maxOfOrNull { maximumRows(it) } ?: 0L,
    )

  private fun execute(sql: String) {
    entityManager.createNativeQuery(sql).executeUpdate()
  }
}
