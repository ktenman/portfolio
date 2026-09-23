package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.job.TransactionRunner
import jakarta.annotation.Resource
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import java.time.LocalDate

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

  @Test
  fun `should load the latest positions of a fund imported after the last table analyze`() {
    val positions =
      transactionRunner.runInTransaction {
        insertPositions(saveFund("VWCE"), 2000)
        execute("ANALYZE etf_position")
        val fund = saveFund("VGLA")
        insertPositions(fund, 5000)
        execute("SET LOCAL statement_timeout = '1s'")
        etfPositionRepository.findLatestPositionsByEtfIds(listOf(fund.id))
      }
    expect(positions).toHaveSize(5000)
  }

  private fun saveFund(symbol: String): Instrument =
    instrumentRepository.save(Instrument(symbol = symbol, name = "$symbol fond", category = "ETF", baseCurrency = "EUR"))

  private fun insertPositions(
    fund: Instrument,
    count: Int,
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
      ).setParameter("symbol", fund.symbol)
      .setParameter("count", count)
      .setParameter("fundId", fund.id)
      .setParameter("snapshotDate", LocalDate.of(2026, 9, 22))
      .executeUpdate()
  }

  private fun execute(sql: String) {
    entityManager.createNativeQuery(sql).executeUpdate()
  }
}
