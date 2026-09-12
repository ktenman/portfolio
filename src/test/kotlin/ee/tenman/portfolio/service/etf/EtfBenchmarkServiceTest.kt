package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createInstrument
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

class EtfBenchmarkServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val etfPositionRepository = mockk<EtfPositionRepository>()
  private val etfBenchmarkService =
    EtfBenchmarkService(instrumentRepository, etfPositionRepository, HoldingAggregationService())
  private val snapshotDate = LocalDate.of(2026, 9, 12)
  private val benchmark = createInstrument(symbol = "VWCE:GER:EUR", id = 11L)

  @Test
  fun `should scale weights to one hundred percent for a fund the portfolio does not hold`() {
    stubPositions(
      position(createHolding(1L, "NVDA", "NVIDIA Corporation"), BigDecimal("4.5000")),
      position(createHolding(2L, "AAPL", "Apple Inc."), BigDecimal("3.5000")),
    )

    val result = etfBenchmarkService.getBenchmarkHoldings("VWCE:GER:EUR")

    expect(result.sumOf { it.percentageOfTotal }).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should keep the relative weighting of each holding when scaling`() {
    stubPositions(
      position(createHolding(1L, "NVDA", "NVIDIA Corporation"), BigDecimal("4.5000")),
      position(createHolding(2L, "AAPL", "Apple Inc."), BigDecimal("1.5000")),
    )

    val result = etfBenchmarkService.getBenchmarkHoldings("VWCE:GER:EUR")

    expect(result.first().percentageOfTotal).toEqualNumerically(BigDecimal("75"))
  }

  @Test
  fun `should merge share classes of the same company into a single row`() {
    stubPositions(
      position(createHolding(1L, "GOOGL", "Alphabet Inc."), BigDecimal("1.2000")),
      position(createHolding(2L, "GOOG", "Alphabet Inc."), BigDecimal("0.8000")),
    )

    val result = etfBenchmarkService.getBenchmarkHoldings("VWCE:GER:EUR")

    expect(result).toHaveSize(1)
  }

  @Test
  fun `should carry the classification of each holding onto the benchmark row`() {
    val holding =
      createHolding(1L, "NESN", "Nestlé S.A.").apply {
        sector = IndustrySector.CONSUMER_ESSENTIALS
        countryCode = "CH"
        countryName = "Switzerland"
      }
    stubPositions(position(holding, BigDecimal("0.3305")))

    val result = etfBenchmarkService.getBenchmarkHoldings("VWCE:GER:EUR")

    expect(result.single().holdingCountryName).toEqual("Switzerland")
  }

  @Test
  fun `should name the benchmark fund as the only source of every row`() {
    stubPositions(position(createHolding(1L, "NVDA", "NVIDIA Corporation"), BigDecimal("4.5000")))

    val result = etfBenchmarkService.getBenchmarkHoldings("VWCE:GER:EUR")

    expect(result.single().inEtfs).toEqual("VWCE:GER:EUR")
  }

  @Test
  fun `should carry no portfolio value on a benchmark row`() {
    stubPositions(position(createHolding(1L, "NVDA", "NVIDIA Corporation"), BigDecimal("4.5000")))

    val result = etfBenchmarkService.getBenchmarkHoldings("VWCE:GER:EUR")

    expect(result.single().totalValueEur).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should return nothing when the benchmark symbol is unknown`() {
    every { instrumentRepository.findBySymbol("MISSING:GER:EUR") } returns Optional.empty()

    val result = etfBenchmarkService.getBenchmarkHoldings("MISSING:GER:EUR")

    expect(result).toBeEmpty()
  }

  @Test
  fun `should return nothing when the benchmark fund has no ingested positions`() {
    stubPositions()

    val result = etfBenchmarkService.getBenchmarkHoldings("VWCE:GER:EUR")

    expect(result).toBeEmpty()
  }

  @Test
  fun `should ignore positions carrying negative weight when scaling the rest`() {
    stubPositions(
      position(createHolding(1L, "NVDA", "NVIDIA Corporation"), BigDecimal("3.0000")),
      position(createHolding(2L, "FWD", "Currency forward"), BigDecimal("-1.0000")),
    )

    val result = etfBenchmarkService.getBenchmarkHoldings("VWCE:GER:EUR")

    expect(result.single().percentageOfTotal).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should return nothing when every position of the benchmark fund carries no weight`() {
    stubPositions(
      position(createHolding(1L, "BTC", "Bitcoin"), BigDecimal.ZERO),
      position(createHolding(2L, "EUR", "Euro"), BigDecimal.ZERO),
    )

    val result = etfBenchmarkService.getBenchmarkHoldings("VWCE:GER:EUR")

    expect(result).toBeEmpty()
  }

  private fun stubPositions(vararg positions: EtfPosition) {
    every { instrumentRepository.findBySymbol("VWCE:GER:EUR") } returns Optional.of(benchmark)
    every { etfPositionRepository.findLatestPositionsByEtfId(11L) } returns positions.toList()
  }

  private fun position(
    holding: EtfHolding,
    weight: BigDecimal,
  ): EtfPosition =
    EtfPosition(
      etfInstrument = benchmark,
      holding = holding,
      weightPercentage = weight,
      snapshotDate = snapshotDate,
    )

  private fun createHolding(
    id: Long,
    ticker: String,
    name: String,
  ): EtfHolding =
    EtfHolding(
      ticker = ticker,
      name = name,
    ).apply { this.id = id }
}
