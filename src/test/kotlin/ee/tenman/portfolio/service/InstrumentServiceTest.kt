package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class InstrumentServiceTest {
  @Mock
  private lateinit var instrumentRepository: InstrumentRepository

  @Mock
  private lateinit var portfolioTransactionRepository: PortfolioTransactionRepository

  @Mock
  private lateinit var investmentMetricsService: InvestmentMetricsService

  @Mock
  private lateinit var dailyPriceService: DailyPriceService

  @Mock
  private lateinit var clock: Clock

  @InjectMocks
  private lateinit var instrumentService: InstrumentService

  private lateinit var testInstrument: Instrument
  private val testDate = LocalDate.of(2024, 1, 15)
  private val fixedInstant = Instant.parse("2024-01-15T10:00:00Z")

  @BeforeEach
  fun setUp() {
    testInstrument =
      Instrument(
        symbol = "AAPL",
        name = "Apple Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("150.00"),
        providerName = ProviderName.ALPHA_VANTAGE,
      ).apply {
        id = 1L
      }

    lenient().whenever(clock.instant()).thenReturn(fixedInstant)
    lenient().whenever(clock.zone).thenReturn(ZoneId.systemDefault())
  }

  @Test
  fun `getInstrumentById returns instrument when found`() {
    whenever(instrumentRepository.findById(1L)).thenReturn(Optional.of(testInstrument))

    val result = instrumentService.getInstrumentById(1L)

    assertThat(result).isEqualTo(testInstrument)
    assertThat(result.symbol).isEqualTo("AAPL")
    verify(instrumentRepository).findById(1L)
  }

  @Test
  fun `getInstrumentById throws exception when instrument not found`() {
    whenever(instrumentRepository.findById(999L)).thenReturn(Optional.empty())

    assertThatThrownBy { instrumentService.getInstrumentById(999L) }
      .isInstanceOf(RuntimeException::class.java)
      .hasMessage("Instrument not found with id: 999")
  }

  @Test
  fun `saveInstrument saves and returns instrument`() {
    whenever(instrumentRepository.save(testInstrument)).thenReturn(testInstrument)

    val result = instrumentService.saveInstrument(testInstrument)

    assertThat(result).isEqualTo(testInstrument)
    verify(instrumentRepository).save(testInstrument)
  }

  @Test
  fun `deleteInstrument calls repository delete`() {
    instrumentService.deleteInstrument(1L)

    verify(instrumentRepository).deleteById(1L)
  }

  @Test
  fun `getAllInstruments without platforms returns all instruments with metrics`() {
    val transactions =
      listOf(
        createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100")),
      )

    val metrics =
      InvestmentMetricsService.InstrumentMetrics(
        totalInvestment = BigDecimal("1000"),
        currentValue = BigDecimal("1500"),
        profit = BigDecimal("500"),
        xirr = 25.0,
        quantity = BigDecimal("10"),
      )

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(transactions)
    whenever(
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        eq(testInstrument),
        any(),
        any(),
      ),
    ).thenReturn(metrics)
    whenever(dailyPriceService.getLastPriceChange(testInstrument))
      .thenReturn(PriceChange(BigDecimal("5.00"), 3.5))

    val result = instrumentService.getAllInstruments()

    assertThat(result).hasSize(1)
    val instrument = result[0]
    assertThat(instrument.totalInvestment).isEqualByComparingTo(BigDecimal("1000"))
    assertThat(instrument.currentValue).isEqualByComparingTo(BigDecimal("1500"))
    assertThat(instrument.profit).isEqualByComparingTo(BigDecimal("500"))
    assertThat(instrument.xirr).isEqualTo(25.0)
    assertThat(instrument.quantity).isEqualByComparingTo(BigDecimal("10"))
    assertThat(instrument.priceChangeAmount).isEqualByComparingTo(BigDecimal("50.00"))
    assertThat(instrument.priceChangePercent).isEqualTo(3.5)
  }

  @Test
  fun `getAllInstruments with platform filter returns only matching instruments`() {
    val lhvTransaction =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        platform = Platform.LHV,
      )
    val lightyearTransaction =
      createBuyTransaction(
        quantity = BigDecimal("5"),
        price = BigDecimal("100"),
        platform = Platform.LIGHTYEAR,
      )

    val metrics =
      InvestmentMetricsService.InstrumentMetrics(
        totalInvestment = BigDecimal("1000"),
        currentValue = BigDecimal("1500"),
        profit = BigDecimal("500"),
        xirr = 25.0,
        quantity = BigDecimal("10"),
      )

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments())
      .thenReturn(listOf(lhvTransaction, lightyearTransaction))
    whenever(
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        eq(testInstrument),
        eq(listOf(lhvTransaction)),
        any(),
      ),
    ).thenReturn(metrics)
    whenever(dailyPriceService.getLastPriceChange(testInstrument)).thenReturn(null)

    val result = instrumentService.getAllInstruments(listOf("lhv"))

    assertThat(result).hasSize(1)
    assertThat(result[0].platforms).containsExactly(Platform.LHV)
  }

  @Test
  fun `getAllInstruments with invalid platform filter ignores invalid platforms`() {
    val transaction =
      createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val metrics =
      InvestmentMetricsService.InstrumentMetrics(
        totalInvestment = BigDecimal("1000"),
        currentValue = BigDecimal("1500"),
        profit = BigDecimal("500"),
        xirr = 25.0,
        quantity = BigDecimal("10"),
      )

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(listOf(transaction))
    whenever(
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        eq(testInstrument),
        any(),
        any(),
      ),
    ).thenReturn(metrics)
    whenever(dailyPriceService.getLastPriceChange(testInstrument)).thenReturn(null)

    val result = instrumentService.getAllInstruments(listOf("invalid_platform", "lhv"))

    assertThat(result).hasSize(1)
  }

  @Test
  fun `getAllInstruments excludes instruments with zero quantity and zero investment when platform filter applied`() {
    val transaction =
      createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val metrics =
      InvestmentMetricsService.InstrumentMetrics(
        totalInvestment = BigDecimal.ZERO,
        currentValue = BigDecimal.ZERO,
        profit = BigDecimal.ZERO,
        xirr = 0.0,
        quantity = BigDecimal.ZERO,
      )

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(listOf(transaction))
    whenever(
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        eq(testInstrument),
        any(),
        any(),
      ),
    ).thenReturn(metrics)

    val result = instrumentService.getAllInstruments(listOf("lhv"))

    assertThat(result).isEmpty()
  }

  @Test
  fun `getAllInstruments includes instruments with zero quantity but positive investment when platform filter applied`() {
    val transaction =
      createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val metrics =
      InvestmentMetricsService.InstrumentMetrics(
        totalInvestment = BigDecimal("1000"),
        currentValue = BigDecimal.ZERO,
        profit = BigDecimal("-1000"),
        xirr = -100.0,
        quantity = BigDecimal.ZERO,
      )

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(listOf(transaction))
    whenever(
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        eq(testInstrument),
        eq(listOf(transaction)),
        any(),
      ),
    ).thenReturn(metrics)
    whenever(dailyPriceService.getLastPriceChange(testInstrument)).thenReturn(null)

    val result = instrumentService.getAllInstruments(listOf("lhv"))

    assertThat(result).hasSize(1)
    assertThat(result[0].totalInvestment).isEqualByComparingTo(BigDecimal("1000"))
  }

  @Test
  fun `getAllInstruments with no transactions for instrument returns empty when platform filter applied`() {
    val anotherInstrument =
      Instrument(
        symbol = "GOOGL",
        name = "Alphabet Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("2800"),
      ).apply { id = 2L }

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument, anotherInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(emptyList())

    val result = instrumentService.getAllInstruments(listOf("lhv"))

    assertThat(result).isEmpty()
  }

  @Test
  fun `getAllInstruments with no transactions for instrument returns instrument when no platform filter`() {
    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(emptyList())

    val result = instrumentService.getAllInstruments()

    assertThat(result).hasSize(1)
    assertThat(result[0]).isEqualTo(testInstrument)
  }

  @Test
  fun `getAllInstruments calculates price change correctly`() {
    val transaction =
      createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val metrics =
      InvestmentMetricsService.InstrumentMetrics(
        totalInvestment = BigDecimal("1000"),
        currentValue = BigDecimal("1500"),
        profit = BigDecimal("500"),
        xirr = 25.0,
        quantity = BigDecimal("10"),
      )

    val priceChange = PriceChange(BigDecimal("5.00"), 3.5)

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(listOf(transaction))
    whenever(
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        eq(testInstrument),
        any(),
        any(),
      ),
    ).thenReturn(metrics)
    whenever(dailyPriceService.getLastPriceChange(testInstrument)).thenReturn(priceChange)

    val result = instrumentService.getAllInstruments()

    assertThat(result).hasSize(1)
    assertThat(result[0].priceChangeAmount).isEqualByComparingTo(BigDecimal("50.00"))
    assertThat(result[0].priceChangePercent).isEqualTo(3.5)
  }

  @Test
  fun `getAllInstruments handles null price change`() {
    val transaction =
      createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val metrics =
      InvestmentMetricsService.InstrumentMetrics(
        totalInvestment = BigDecimal("1000"),
        currentValue = BigDecimal("1500"),
        profit = BigDecimal("500"),
        xirr = 25.0,
        quantity = BigDecimal("10"),
      )

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(listOf(transaction))
    whenever(
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        eq(testInstrument),
        any(),
        any(),
      ),
    ).thenReturn(metrics)
    whenever(dailyPriceService.getLastPriceChange(testInstrument)).thenReturn(null)

    val result = instrumentService.getAllInstruments()

    assertThat(result).hasSize(1)
    assertThat(result[0].priceChangeAmount).isNull()
    assertThat(result[0].priceChangePercent).isNull()
  }

  @Test
  fun `getAllInstruments with multiple platforms aggregates correctly`() {
    val lhvTx =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        platform = Platform.LHV,
      )
    val lightyearTx =
      createBuyTransaction(
        quantity = BigDecimal("5"),
        price = BigDecimal("110"),
        platform = Platform.LIGHTYEAR,
      )

    val metrics =
      InvestmentMetricsService.InstrumentMetrics(
        totalInvestment = BigDecimal("1550"),
        currentValue = BigDecimal("2250"),
        profit = BigDecimal("700"),
        xirr = 30.0,
        quantity = BigDecimal("15"),
      )

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments())
      .thenReturn(listOf(lhvTx, lightyearTx))
    whenever(
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        eq(testInstrument),
        any(),
        any(),
      ),
    ).thenReturn(metrics)
    whenever(dailyPriceService.getLastPriceChange(testInstrument)).thenReturn(null)

    val result = instrumentService.getAllInstruments()

    assertThat(result).hasSize(1)
    assertThat(result[0].platforms).containsExactlyInAnyOrder(Platform.LHV, Platform.LIGHTYEAR)
  }

  @Test
  fun `getAllInstruments with platform filter matching multiple platforms`() {
    val lhvTx =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        platform = Platform.LHV,
      )
    val lightyearTx =
      createBuyTransaction(
        quantity = BigDecimal("5"),
        price = BigDecimal("110"),
        platform = Platform.LIGHTYEAR,
      )

    val metrics =
      InvestmentMetricsService.InstrumentMetrics(
        totalInvestment = BigDecimal("1550"),
        currentValue = BigDecimal("2250"),
        profit = BigDecimal("700"),
        xirr = 30.0,
        quantity = BigDecimal("15"),
      )

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments())
      .thenReturn(listOf(lhvTx, lightyearTx))
    whenever(
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        eq(testInstrument),
        eq(listOf(lhvTx, lightyearTx)),
        any(),
      ),
    ).thenReturn(metrics)
    whenever(dailyPriceService.getLastPriceChange(testInstrument)).thenReturn(null)

    val result = instrumentService.getAllInstruments(listOf("lhv", "lightyear"))

    assertThat(result).hasSize(1)
    assertThat(result[0].platforms).containsExactlyInAnyOrder(Platform.LHV, Platform.LIGHTYEAR)
  }

  @Test
  fun `getAllInstruments with mixed case platform names`() {
    val transaction =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        platform = Platform.LHV,
      )

    val metrics =
      InvestmentMetricsService.InstrumentMetrics(
        totalInvestment = BigDecimal("1000"),
        currentValue = BigDecimal("1500"),
        profit = BigDecimal("500"),
        xirr = 25.0,
        quantity = BigDecimal("10"),
      )

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(listOf(transaction))
    whenever(
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        eq(testInstrument),
        eq(listOf(transaction)),
        any(),
      ),
    ).thenReturn(metrics)
    whenever(dailyPriceService.getLastPriceChange(testInstrument)).thenReturn(null)

    val result = instrumentService.getAllInstruments(listOf("Lhv", "LIGHTYEAR"))

    assertThat(result).hasSize(1)
  }

  @Test
  fun `getAllInstruments with empty platform list filters out instruments with no transactions`() {
    val transaction =
      createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(listOf(transaction))

    val result = instrumentService.getAllInstruments(emptyList())

    assertThat(result).isEmpty()
  }

  @Test
  fun `getAllInstruments filters out instruments with no matching platform transactions`() {
    val lhvTransaction =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        platform = Platform.LHV,
      )

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(listOf(lhvTransaction))

    val result = instrumentService.getAllInstruments(listOf("LIGHTYEAR"))

    assertThat(result).isEmpty()
  }

  @Test
  fun `getAllInstruments with multiple instruments some with matching platforms`() {
    val instrument2 =
      Instrument(
        symbol = "GOOGL",
        name = "Alphabet Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("2800"),
      ).apply { id = 2L }

    val tx1 =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        platform = Platform.LHV,
      )
    val tx2 =
      createBuyTransaction(
        quantity = BigDecimal("5"),
        price = BigDecimal("2500"),
        platform = Platform.LIGHTYEAR,
        instrument = instrument2,
      )

    val metrics1 =
      InvestmentMetricsService.InstrumentMetrics(
        totalInvestment = BigDecimal("1000"),
        currentValue = BigDecimal("1500"),
        profit = BigDecimal("500"),
        xirr = 25.0,
        quantity = BigDecimal("10"),
      )

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument, instrument2))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(listOf(tx1, tx2))
    whenever(
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        eq(testInstrument),
        eq(listOf(tx1)),
        any(),
      ),
    ).thenReturn(metrics1)
    whenever(dailyPriceService.getLastPriceChange(testInstrument)).thenReturn(null)

    val result = instrumentService.getAllInstruments(listOf("lhv"))

    assertThat(result).hasSize(1)
    assertThat(result[0].symbol).isEqualTo("AAPL")
  }

  @Test
  fun `getAllInstruments uses current date from clock`() {
    val transaction =
      createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))

    val metrics =
      InvestmentMetricsService.InstrumentMetrics(
        totalInvestment = BigDecimal("1000"),
        currentValue = BigDecimal("1500"),
        profit = BigDecimal("500"),
        xirr = 25.0,
        quantity = BigDecimal("10"),
      )

    whenever(instrumentRepository.findAll()).thenReturn(listOf(testInstrument))
    whenever(portfolioTransactionRepository.findAllWithInstruments()).thenReturn(listOf(transaction))
    whenever(
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        eq(testInstrument),
        any(),
        eq(testDate),
      ),
    ).thenReturn(metrics)
    whenever(dailyPriceService.getLastPriceChange(testInstrument)).thenReturn(null)

    instrumentService.getAllInstruments()

    verify(investmentMetricsService).calculateInstrumentMetricsWithProfits(
      eq(testInstrument),
      any(),
      eq(testDate),
    )
  }

  private fun createBuyTransaction(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = testDate,
    commission: BigDecimal = BigDecimal("5"),
    platform: Platform = Platform.LHV,
    instrument: Instrument = testInstrument,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = instrument,
      transactionType = TransactionType.BUY,
      quantity = quantity,
      price = price,
      transactionDate = date,
      platform = platform,
      commission = commission,
    )
}
