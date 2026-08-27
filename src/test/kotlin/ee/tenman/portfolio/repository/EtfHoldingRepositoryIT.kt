package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.notToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.SectorSource
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class EtfHoldingRepositoryIT {
  @Resource
  private lateinit var etfHoldingRepository: EtfHoldingRepository

  @Resource
  private lateinit var etfPositionRepository: EtfPositionRepository

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  private lateinit var etf: Instrument

  @BeforeEach
  fun setUp() {
    etfPositionRepository.deleteAll()
    etfHoldingRepository.deleteAll()
    etf =
      instrumentRepository.save(
        Instrument(symbol = "TSTETF", name = "Tarkvarafond Õnnelik", category = "ETF", baseCurrency = "EUR"),
      )
  }

  @Test
  fun `should not return holdings whose sector came from Lightyear`() {
    persist("Tundmatu Ühistu OÜ", null, null)
    val lightyearHolding = persist("Kärbes Semiconductors AS", IndustrySector.SEMICONDUCTORS, SectorSource.LIGHTYEAR)

    val unclassified = etfHoldingRepository.findUnclassifiedSectorHoldings(3).map { it.name }

    expect(unclassified).notToContain(lightyearHolding.name)
  }

  @Test
  fun `should return holdings that have no sector at all`() {
    val bare = persist("Tundmatu Ühistu OÜ", null, null)

    val unclassified = etfHoldingRepository.findUnclassifiedSectorHoldings(3).map { it.name }

    expect(unclassified).toContain(bare.name)
  }

  @Test
  fun `should not return holdings that exhausted their fetch attempts`() {
    persist("Tundmatu Ühistu OÜ", null, null)
    val exhausted = persist("Läbikukkunud Vahendus AS", null, null, attempts = 3)

    val unclassified = etfHoldingRepository.findUnclassifiedSectorHoldings(3).map { it.name }

    expect(unclassified).notToContain(exhausted.name)
  }

  private fun persist(
    name: String,
    sector: IndustrySector?,
    source: SectorSource?,
    attempts: Int = 0,
  ): EtfHolding {
    val holding =
      etfHoldingRepository.save(
        EtfHolding(name = name, sector = sector, sectorSource = source, sectorFetchAttempts = attempts),
      )
    etfPositionRepository.save(
      EtfPosition(
        etfInstrument = etf,
        holding = holding,
        snapshotDate = LocalDate.of(2026, 8, 27),
        weightPercentage = BigDecimal("1.5"),
      ),
    )
    return holding
  }
}
