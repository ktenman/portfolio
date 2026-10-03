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

  @Test
  fun `should keep the version when the same prices are written again`() {
    val fund = fund(instruments)
    repeat(2) { upsert(fund, null, "12.34") }
    expect(repository.findAll().single().version).toEqual(0L)
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

  private fun upsert(
    fund: Instrument,
    open: String?,
    close: String,
  ) = runner.runInTransaction {
    repository.upsert(fund.id, LocalDate.of(2026, 10, 2), "FT", open?.let(::BigDecimal), null, null, BigDecimal(close), null)
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

  @Test
  fun `should keep the version when the same price is written again`() {
    val fund = fund(instruments)
    repeat(2) { upsert(fund, "45.67") }
    expect(repository.findAll().single().version).toEqual(0L)
  }

  @Test
  fun `should store a changed price for the same hour`() {
    val fund = fund(instruments)
    upsert(fund, "45.67")
    upsert(fund, "45.68")
    expect(repository.findAll().single().price).toEqualNumerically(BigDecimal("45.68"))
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
