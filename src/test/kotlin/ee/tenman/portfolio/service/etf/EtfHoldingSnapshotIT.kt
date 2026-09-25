package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class EtfHoldingSnapshotIT {
  @Resource
  private lateinit var service: EtfHoldingPersistenceService

  @Resource
  private lateinit var instruments: InstrumentRepository

  @Resource
  private lateinit var holdings: EtfHoldingRepository

  @Resource
  private lateinit var positions: EtfPositionRepository

  @Resource
  private lateinit var transactions: PlatformTransactionManager

  @BeforeEach
  fun setUp() {
    listOf("VWCE", "IITU").forEach { symbol ->
      instruments.save(
        Instrument(symbol = symbol, name = symbol, category = "ETF", baseCurrency = "EUR", providerName = ProviderName.LIGHTYEAR),
      )
    }
  }

  @Test
  fun `should replace disappeared constituents in a same day snapshot`() {
    val date = LocalDate.of(2026, 9, 25)
    service.saveHoldings("VWCE", date, listOf(holding("Äri A", "50", 1), holding("Äri B", "50", 2)))

    service.saveHoldings("VWCE", date, listOf(holding("Äri A", "60", 1), holding("Äri C", "40", 2)))

    val snapshot = positions.findLatestPositionsByEtfId(instruments.findBySymbol("VWCE").orElseThrow().id)
    expect(snapshot.map { it.holding.name }).toContainExactly("Äri A", "Äri C")
    expect(snapshot.map { it.positionRank }).toContainExactly(1, 2)
    expect(snapshot.first().weightPercentage).toEqualNumerically(BigDecimal("60"))
    expect(snapshot.sumOf { it.weightPercentage }).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should preserve other snapshots and shared holding entities during replacement`() {
    val date = LocalDate.of(2026, 9, 25)
    val initial = listOf(holding("Äri A", "50", 1), holding("Äri B", "50", 2))
    service.saveHoldings("VWCE", date.minusDays(1), initial)
    service.saveHoldings("IITU", date, initial)
    service.saveHoldings("VWCE", date, initial)
    val shared = holdings.findByNameIgnoreCase("Äri B")!!.id
    val preserved =
      positions.findAll().filter {
        it.snapshotDate != date ||
      it.etfInstrument.id == instruments.findBySymbol("IITU").orElseThrow().id
          }

    service.saveHoldings("VWCE", date, listOf(holding("Äri A", "60", 1), holding("Äri C", "40", 2)))

    expect(positions.findAllById(preserved.map { it.id })).toHaveSize(4)
    expect(positions.findByHoldingId(shared)).toHaveSize(2)
    expect(holdings.findById(shared).orElseThrow().name).toEqual("Äri B")
    expect(holdings.findAll()).toHaveSize(3)
  }

  @Test
  fun `should roll back a replacement when a later holding cannot be persisted`() {
    val date = LocalDate.of(2026, 9, 25)
    service.saveHoldings("VWCE", date, listOf(holding("Äri A", "50", 1), holding("Äri B", "50", 2)))
    val invalid = holding("Äri C", "40", 2).copy(ticker = "X".repeat(256))

    expect { service.saveHoldings("VWCE", date, listOf(holding("Äri A", "60", 1), invalid)) }
      .toThrow<DataIntegrityViolationException>()

    val snapshot = positions.findLatestPositionsByEtfId(instruments.findBySymbol("VWCE").orElseThrow().id)
    expect(snapshot.map { it.holding.name }.toSet()).toEqual(setOf("Äri A", "Äri B"))
    expect(snapshot.map { it.weightPercentage.toInt() }).toContainExactly(50, 50)
    expect(holdings.findAll().map { it.name }.toSet()).toEqual(setOf("Äri A", "Äri B"))
  }

  @Test
  fun `should roll back removed positions with the surrounding transaction`() {
    val date = LocalDate.of(2026, 9, 25)
    service.saveHoldings("VWCE", date, listOf(holding("Äri A", "50", 1), holding("Äri B", "50", 2)))

    expect {
      TransactionTemplate(transactions).executeWithoutResult {
        service.saveHoldings("VWCE", date, listOf(holding("Äri A", "60", 1), holding("Äri C", "40", 2)))
        error("Abort snapshot transaction")
      }
    }.toThrow<IllegalStateException>()

    val snapshot = positions.findLatestPositionsByEtfId(instruments.findBySymbol("VWCE").orElseThrow().id)
    expect(snapshot.map { it.holding.name }.toSet()).toEqual(setOf("Äri A", "Äri B"))
    expect(snapshot.map { it.weightPercentage.toInt() }).toContainExactly(50, 50)
    expect(holdings.findAll().map { it.name }.toSet()).toEqual(setOf("Äri A", "Äri B"))
  }

  @Test
  fun `cannot erase a valid snapshot with empty holdings`() {
    val date = LocalDate.of(2026, 9, 25)
    service.saveHoldings("VWCE", date, listOf(holding("Äri A", "100", 1)))

    expect { service.saveHoldings("VWCE", date, emptyList()) }.toThrow<IllegalArgumentException>()

    val snapshot = positions.findLatestPositionsByEtfId(instruments.findBySymbol("VWCE").orElseThrow().id)
    expect(snapshot.map { it.holding.name }).toContainExactly("Äri A")
    expect(snapshot.single().weightPercentage).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `cannot replace a valid snapshot with an invalid holding`() {
    val date = LocalDate.of(2026, 9, 25)
    service.saveHoldings("VWCE", date, listOf(holding("Äri A", "100", 1)))

    expect { service.saveHoldings("VWCE", date, listOf(holding("Äri A", "60", 1), holding(" ", "40", 2))) }
      .toThrow<IllegalArgumentException>()

    val snapshot = positions.findLatestPositionsByEtfId(instruments.findBySymbol("VWCE").orElseThrow().id)
    expect(snapshot.map { it.holding.name }).toContainExactly("Äri A")
    expect(snapshot.single().weightPercentage).toEqualNumerically(BigDecimal("100"))
  }

  private fun holding(
    name: String,
    weight: String,
    rank: Int,
  ): HoldingData = HoldingData(name = name, ticker = null, sector = null, weight = BigDecimal(weight), rank = rank)
}
