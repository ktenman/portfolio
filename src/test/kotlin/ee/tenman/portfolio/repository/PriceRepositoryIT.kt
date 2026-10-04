package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.job.TransactionRunner
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@IntegrationTest
class DailyPriceRepositoryIT {
  @Resource
  private lateinit var repository: DailyPriceRepository

  @Resource
  private lateinit var instruments: InstrumentRepository

  @Resource
  private lateinit var runner: TransactionRunner

  @Resource
  private lateinit var jdbc: JdbcTemplate

  @Test
  fun `should keep the version when the same prices are written again`() {
    val fund = fund(instruments)
    repeat(2) { upsert(fund, null, "12.34") }
    expect(repository.findAll().single().version).toEqual(0L)
  }

  @Test
  fun `should leave the row unlocked when the same prices are written again`() {
    val fund = fund(instruments)
    repeat(2) { upsert(fund, null, "12.34") }
    expect(xmax(jdbc, "daily_price")).toEqual(0L)
  }

  @Test
  fun `should not consume an id when the same prices are written again`() {
    val fund = fund(instruments)
    upsert(fund, null, "12.34")
    val id = lastId(jdbc, "daily_price")
    upsert(fund, null, "12.34")
    expect(lastId(jdbc, "daily_price")).toEqual(id)
  }

  @Test
  fun `should keep the version when prices finer than the column scale are written again`() {
    val fund = fund(instruments)
    repeat(2) { upsert(fund, "12.3456789012345", "12.3456789012345") }
    expect(repository.findAll().single().version).toEqual(0L)
  }

  @Test
  fun `should raise the version when the close price changes`() {
    val fund = fund(instruments)
    upsert(fund, null, "12.34")
    upsert(fund, null, "12.35")
    expect(repository.findAll().single().version).toEqual(1L)
  }

  @Test
  fun `should store a changed close price for the same day`() {
    val fund = fund(instruments)
    upsert(fund, null, "12.34")
    upsert(fund, null, "12.35")
    expect(repository.findAll().single().closePrice).toEqualNumerically(BigDecimal("12.35"))
  }

  @Test
  fun `should store an open price that arrives after the close price`() {
    val fund = fund(instruments)
    upsert(fund, null, "12.34")
    upsert(fund, "12.10", "12.34")
    expect(repository.findAll().single().openPrice).notToEqualNull().toEqualNumerically(BigDecimal("12.10"))
  }

  @Test
  fun `should clear the open price when a later write has none`() {
    val fund = fund(instruments)
    upsert(fund, "12.10", "12.34")
    upsert(fund, null, "12.34")
    expect(repository.findAll().single().openPrice).toEqual(null)
  }

  @Test
  fun `should store a changed volume for the same day`() {
    val fund = fund(instruments)
    upsert(fund, null, "12.34", 100)
    upsert(fund, null, "12.34", 250)
    expect(repository.findAll().single().volume).toEqual(250L)
  }

  @Test
  fun `should return only the latest price point on or before the date`() {
    val fund = fund(instruments)
    upsert(fund, null, "11.00", date = LocalDate.of(2026, 10, 1))
    upsert(fund, null, "12.00", date = LocalDate.of(2026, 10, 2))
    upsert(fund, null, "13.00", date = LocalDate.of(2026, 10, 3))
    val points = repository.findLatestPricePoints(listOf(fund), LocalDate.of(2026, 10, 2))
    expect(points.single().closePrice).toEqualNumerically(BigDecimal("12.00"))
  }

  private fun upsert(
    fund: Instrument,
    open: String?,
    close: String,
    volume: Long? = null,
    date: LocalDate = LocalDate.of(2026, 10, 2),
  ) = runner.runInTransaction {
    repository.upsert(fund.id, date, "FT", open?.let(::BigDecimal), null, null, BigDecimal(close), volume)
  }
}

@IntegrationTest
class PriceSnapshotRepositoryIT {
  @Resource
  private lateinit var repository: PriceSnapshotRepository

  @Resource
  private lateinit var instruments: InstrumentRepository

  @Resource
  private lateinit var runner: TransactionRunner

  @Resource
  private lateinit var jdbc: JdbcTemplate

  @Test
  fun `should keep the version when the same price is written again`() {
    val fund = fund(instruments)
    repeat(2) { upsert(fund, "45.67") }
    expect(repository.findAll().single().version).toEqual(0L)
  }

  @Test
  fun `should leave the row unlocked when the same price is written again`() {
    val fund = fund(instruments)
    repeat(2) { upsert(fund, "45.67") }
    expect(xmax(jdbc, "price_snapshot")).toEqual(0L)
  }

  @Test
  fun `should not consume an id when the same price is written again`() {
    val fund = fund(instruments)
    upsert(fund, "45.67")
    val id = lastId(jdbc, "price_snapshot")
    upsert(fund, "45.67")
    expect(lastId(jdbc, "price_snapshot")).toEqual(id)
  }

  @Test
  fun `should keep the version when a price finer than the column scale is written again`() {
    val fund = fund(instruments)
    repeat(2) { upsert(fund, "45.678901234567") }
    expect(repository.findAll().single().version).toEqual(0L)
  }

  @Test
  fun `should store a changed price for the same hour`() {
    val fund = fund(instruments)
    upsert(fund, "45.67")
    upsert(fund, "45.68")
    expect(repository.findAll().single().price).toEqualNumerically(BigDecimal("45.68"))
  }

  @Test
  fun `should raise the version when the price changes`() {
    val fund = fund(instruments)
    upsert(fund, "45.67")
    upsert(fund, "45.68")
    expect(repository.findAll().single().version).toEqual(1L)
  }

  private fun upsert(
    fund: Instrument,
    price: String,
  ) = runner.runInTransaction {
    repository.upsert(fund.id, "LIGHTYEAR", Instant.parse("2026-10-02T14:00:00Z"), BigDecimal(price))
  }
}

private fun fund(instruments: InstrumentRepository): Instrument =
  instruments.save(Instrument(symbol = "ÕUN", name = "Õunake fond", category = "ETF", baseCurrency = "EUR"))

private fun lastId(
  jdbc: JdbcTemplate,
  table: String,
): Long? = jdbc.queryForObject("SELECT last_value FROM ${table}_id_seq", Long::class.java)

private fun xmax(
  jdbc: JdbcTemplate,
  table: String,
): Long? = jdbc.queryForObject("SELECT xmax::text::bigint FROM $table", Long::class.java)
