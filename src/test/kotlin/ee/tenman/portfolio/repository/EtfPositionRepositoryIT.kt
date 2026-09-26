package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.job.TransactionRunner
import jakarta.annotation.Resource
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import java.time.LocalDate
import javax.sql.DataSource

@IntegrationTest
class EtfPositionRepositoryIT {
  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var etfPositionRepository: EtfPositionRepository

  @Resource
  private lateinit var entityManager: EntityManager

  @Resource
  private lateinit var transactionRunner: TransactionRunner

  @Resource
  private lateinit var dataSource: DataSource

  @Resource
  private lateinit var etfHoldingRepository: EtfHoldingRepository

  @Test
  fun `should load the latest positions of a fund imported after the last table analyze`() {
    val positions =
      transactionRunner.runInTransaction {
        insertPositions(saveFund("VWCE"), 2000)
        execute("ANALYZE etf_position, etf_holding")
        val fund = saveFund("VGLA")
        insertPositions(fund, 5000)
        execute("SET LOCAL statement_timeout = '1s'")
        etfPositionRepository.findLatestPositionsByEtfIds(listOf(fund.id))
      }
    expect(positions).toHaveSize(5000)
  }

  @Test
  fun `should find the latest snapshot date for the requested fund`() {
    transactionRunner.runInTransaction {
      val fund = saveFund("VWCE:GER:EUR")
      insertPositions(fund, 1, LocalDate.of(2026, 8, 31))
      insertPositions(fund, 1, LocalDate.of(2026, 7, 31))
      insertPositions(saveFund("VGLA:GER:EUR"), 1, LocalDate.of(2026, 9, 22))
    }
    expect(etfPositionRepository.findLatestSnapshotDate("VWCE:GER:EUR")).toEqual(LocalDate.of(2026, 8, 31))
  }

  @ParameterizedTest
  @ValueSource(strings = ["EMPTY:GER:EUR", "MISSING:GER:EUR"])
  fun `should return no snapshot date for a fund without positions`(symbol: String) {
    transactionRunner.runInTransaction { saveFund("EMPTY:GER:EUR") }
    expect(etfPositionRepository.findLatestSnapshotDate(symbol)).toEqual(null)
  }

  @Test
  fun `should delete only the requested funds positions newer than the effective date`() {
    val date = LocalDate.of(2026, 8, 31)
    transactionRunner.runInTransaction {
      val fund = saveFund("VWCE:GER:EUR")
      insertPositions(fund, 1, date.minusMonths(1))
      insertPositions(fund, 1, date)
      insertPositions(fund, 2, date.plusDays(1))
      insertPositions(saveFund("VGLA:GER:EUR"), 1, date.plusDays(1))
    }
    val deleted = etfPositionRepository.deleteBySymbolAndSnapshotDateAfter("VWCE:GER:EUR", date)
    val remaining =
      transactionRunner.runInTransaction {
        etfPositionRepository.findAll().map { "${it.etfInstrument.symbol} ${it.snapshotDate}" }.sorted()
      }
    expect(deleted).toEqual(2)
    expect(remaining).toContainExactly("VGLA:GER:EUR 2026-09-01", "VWCE:GER:EUR 2026-07-31", "VWCE:GER:EUR 2026-08-31")
  }

  @Test
  fun `should make repeated deletion of superseded snapshots a no op`() {
    val date = LocalDate.of(2026, 8, 31)
    transactionRunner.runInTransaction { insertPositions(saveFund("VWCE:GER:EUR"), 1, date.plusDays(1)) }
    etfPositionRepository.deleteBySymbolAndSnapshotDateAfter("VWCE:GER:EUR", date)
    expect(etfPositionRepository.deleteBySymbolAndSnapshotDateAfter("VWCE:GER:EUR", date)).toEqual(0)
  }

  @Test
  fun `should remove old Lightyear positions only for the five newly covered Vanguard funds`() {
    val symbols = listOf("VUAA:GER:EUR", "VWCE:GER:EUR", "VNRA:GER:EUR", "VNRT:AEX:EUR", "VWCG:GER:EUR")
    transactionRunner.runInTransaction {
      (symbols + listOf("VGLA:GER:EUR", "VXUS:GER:EUR", "IUSQ:GER:EUR")).forEach { symbol ->
        val fund = saveFund(symbol)
        insertPositions(fund, 1, LocalDate.of(2026, 8, 31))
        insertPositions(fund, 1, LocalDate.of(2026, 9, 22))
      }
    }
    dataSource.connection.use { connection ->
      ScriptUtils.executeSqlScript(
        connection,
        ClassPathResource("db/migration/V202609231848__remove_lightyear_positions_for_vanguard_funds.sql"),
      )
    }
    val remaining =
      transactionRunner.runInTransaction {
        etfPositionRepository.findAll().map { it.etfInstrument.symbol }.sorted()
      }
    expect(etfHoldingRepository.count()).toEqual(16L)
    expect(remaining).toContainExactly(
      "IUSQ:GER:EUR",
      "IUSQ:GER:EUR",
      "VGLA:GER:EUR",
      "VGLA:GER:EUR",
      "VXUS:GER:EUR",
      "VXUS:GER:EUR",
    )
  }

  private fun saveFund(symbol: String): Instrument =
    instrumentRepository.save(Instrument(symbol = symbol, name = "$symbol fond", category = "ETF", baseCurrency = "EUR"))

  private fun insertPositions(
    fund: Instrument,
    count: Int,
    snapshotDate: LocalDate = LocalDate.of(2026, 9, 22),
  ) {
    entityManager
      .createNativeQuery(
        """
        WITH holding AS (
          INSERT INTO etf_holding (uuid, name)
          SELECT gen_random_uuid(), concat(:symbol, ' osalus ', g) FROM generate_series(1, :count) g
          RETURNING id
        )
        INSERT INTO etf_position (etf_instrument_id, holding_id, snapshot_date, weight_percentage)
        SELECT :fundId, id, :snapshotDate, 0.02 FROM holding
        """,
      ).setParameter("symbol", "${fund.symbol} $snapshotDate")
      .setParameter("count", count)
      .setParameter("fundId", fund.id)
      .setParameter("snapshotDate", snapshotDate)
      .executeUpdate()
  }

  private fun execute(sql: String) {
    entityManager.createNativeQuery(sql).executeUpdate()
  }
}
