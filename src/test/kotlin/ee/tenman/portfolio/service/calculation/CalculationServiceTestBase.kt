package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.summary.SummaryService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.BeforeEach
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

abstract class CalculationServiceTestBase {
  protected val dataRetrievalService = mockk<DailyPriceService>()
  protected val instrumentRepository = mockk<InstrumentRepository>()
  private val clock = mockk<Clock>()
  protected val portfolioSummaryService = mockk<SummaryService>()
  protected val today: LocalDate = LocalDate.of(2024, 5, 15)
  protected val instrumentCode = "QDVE:GER:EUR"
  protected val testInstrument: Instrument =
    TransactionFixtures.createInstrument(
      symbol = instrumentCode,
      name = "iShares S&P 500 Information Technology Sector",
      category = "ETF",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("28.50"),
    )
  protected val calculationService =
    CalculationService(
      dataRetrievalService = dataRetrievalService,
      instrumentRepository = instrumentRepository,
      calculationDispatcher = Dispatchers.Unconfined,
      clock = clock,
      portfolioSummaryService = portfolioSummaryService,
    )

  @BeforeEach
  fun setUp() {
    every { clock.instant() } returns today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    every { clock.zone } returns ZoneId.systemDefault()
    every { instrumentRepository.findBySymbol(instrumentCode) } returns Optional.of(testInstrument)
  }
}
