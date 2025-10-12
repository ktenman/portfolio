package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class InstrumentServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val portfolioTransactionRepository = mockk<PortfolioTransactionRepository>()
  private val investmentMetricsService = mockk<InvestmentMetricsService>()
  private val dailyPriceService = mockk<DailyPriceService>()
  private val clock = mockk<Clock>()
  private val priceUpdateEventService = mockk<PriceUpdateEventService>(relaxed = true)

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

    every { clock.instant() } returns fixedInstant
    every { clock.zone } returns ZoneId.systemDefault()

    instrumentService =
      InstrumentService(
        instrumentRepository,
        portfolioTransactionRepository,
        investmentMetricsService,
        dailyPriceService,
        clock,
        priceUpdateEventService,
      )
  }

  @Test
  fun `should return instrument when found by id`() {
    every { instrumentRepository.findById(1L) } returns Optional.of(testInstrument)

    val result = instrumentService.getInstrumentById(1L)

    expect(result).toEqual(testInstrument)
    expect(result.symbol).toEqual("AAPL")
    verify { instrumentRepository.findById(1L) }
  }

  @Test
  fun `should throw exception when instrument not found by id`() {
    every { instrumentRepository.findById(999L) } returns Optional.empty()

    expect {
      instrumentService.getInstrumentById(999L)
    }.toThrow<RuntimeException> {
      messageToContain("Instrument not found with id: 999")
    }
  }

  @Test
  fun `should save and return instrument when saving`() {
    every { instrumentRepository.save(testInstrument) } returns testInstrument
    every { instrumentRepository.findById(1L) } returns Optional.of(testInstrument)
    every { portfolioTransactionRepository.findAllByInstrumentId(1L) } returns emptyList()

    val result = instrumentService.saveInstrument(testInstrument)

    expect(result).toEqual(testInstrument)
    verify { instrumentRepository.save(testInstrument) }
  }

  @Test
  fun `should call repository delete when deleting instrument`() {
    every { instrumentRepository.deleteById(1L) } returns Unit

    instrumentService.deleteInstrument(1L)

    verify { instrumentRepository.deleteById(1L) }
  }

  @Test
  fun `should return all instruments with metrics when no platform filter specified`() {
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

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns transactions
    every {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        any(),
        any(),
      )
    } returns metrics
    every { dailyPriceService.getLastPriceChange(testInstrument) } returns
      PriceChange(BigDecimal("5.00"), 3.5)

    val result = instrumentService.getAllInstruments()

    expect(result).toHaveSize(1)
    val instrument = result[0]
    expect(instrument.totalInvestment).toEqualNumerically(BigDecimal("1000"))
    expect(instrument.currentValue).toEqualNumerically(BigDecimal("1500"))
    expect(instrument.profit).toEqualNumerically(BigDecimal("500"))
    expect(instrument.xirr).toEqual(25.0)
    expect(instrument.quantity).toEqualNumerically(BigDecimal("10"))
    expect(instrument.priceChangeAmount).notToEqualNull().toEqualNumerically(BigDecimal("50.00"))
    expect(instrument.priceChangePercent).toEqual(3.5)
  }

  @Test
  fun `should return only matching instruments when platform filter specified`() {
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

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns
      listOf(lhvTransaction, lightyearTransaction)
    every {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        listOf(lhvTransaction),
        any(),
      )
    } returns metrics
    every { dailyPriceService.getLastPriceChange(testInstrument) } returns null

    val result = instrumentService.getAllInstruments(listOf("lhv"))

    expect(result).toHaveSize(1)
    expect(result[0].platforms).toContainExactly(Platform.LHV)
  }

  @Test
  fun `should ignore invalid platforms when platform filter contains invalid values`() {
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

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        any(),
        any(),
      )
    } returns metrics
    every { dailyPriceService.getLastPriceChange(testInstrument) } returns null

    val result = instrumentService.getAllInstruments(listOf("invalid_platform", "lhv"))

    expect(result).toHaveSize(1)
  }

  @Test
  fun `should exclude instruments with zero quantity and zero investment when platform filter applied`() {
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

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        any(),
        any(),
      )
    } returns metrics
    every { dailyPriceService.getLastPriceChange(testInstrument) } returns null

    val result = instrumentService.getAllInstruments(listOf("lhv"))

    expect(result).toBeEmpty()
  }

  @Test
  fun `should include instruments with zero quantity but positive investment when platform filter applied`() {
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

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        listOf(transaction),
        any(),
      )
    } returns metrics
    every { dailyPriceService.getLastPriceChange(testInstrument) } returns null

    val result = instrumentService.getAllInstruments(listOf("lhv"))

    expect(result).toHaveSize(1)
    expect(result[0].totalInvestment).toEqualNumerically(BigDecimal("1000"))
  }

  @Test
  fun `should return empty when no transactions for instrument and platform filter applied`() {
    val anotherInstrument =
      Instrument(
        symbol = "GOOGL",
        name = "Alphabet Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("2800"),
      ).apply { id = 2L }

    every { instrumentRepository.findAll() } returns listOf(testInstrument, anotherInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns emptyList()

    val result = instrumentService.getAllInstruments(listOf("lhv"))

    expect(result).toBeEmpty()
  }

  @Test
  fun `should return instrument when no transactions and no platform filter`() {
    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns emptyList()

    val result = instrumentService.getAllInstruments()

    expect(result).toHaveSize(1)
    expect(result[0]).toEqual(testInstrument)
  }

  @Test
  fun `should calculate price change correctly when price change available`() {
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

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        any(),
        any(),
      )
    } returns metrics
    every { dailyPriceService.getLastPriceChange(testInstrument) } returns priceChange

    val result = instrumentService.getAllInstruments()

    expect(result).toHaveSize(1)
    expect(result[0].priceChangeAmount).notToEqualNull().toEqualNumerically(BigDecimal("50.00"))
    expect(result[0].priceChangePercent).toEqual(3.5)
  }

  @Test
  fun `should handle null price change when price change not available`() {
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

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        any(),
        any(),
      )
    } returns metrics
    every { dailyPriceService.getLastPriceChange(testInstrument) } returns null

    val result = instrumentService.getAllInstruments()

    expect(result).toHaveSize(1)
    expect(result[0].priceChangeAmount).toEqual(null)
    expect(result[0].priceChangePercent).toEqual(null)
  }

  @Test
  fun `should aggregate correctly when multiple platforms exist`() {
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

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns
      listOf(lhvTx, lightyearTx)
    every {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        any(),
        any(),
      )
    } returns metrics
    every { dailyPriceService.getLastPriceChange(testInstrument) } returns null

    val result = instrumentService.getAllInstruments()

    expect(result).toHaveSize(1)
    expect(result[0].platforms.toSet()).toEqual(setOf(Platform.LHV, Platform.LIGHTYEAR))
  }

  @Test
  fun `should return instruments matching multiple platforms when filter includes multiple platforms`() {
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

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns
      listOf(lhvTx, lightyearTx)
    every {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        listOf(lhvTx, lightyearTx),
        any(),
      )
    } returns metrics
    every { dailyPriceService.getLastPriceChange(testInstrument) } returns null

    val result = instrumentService.getAllInstruments(listOf("lhv", "lightyear"))

    expect(result).toHaveSize(1)
    expect(result[0].platforms.toSet()).toEqual(setOf(Platform.LHV, Platform.LIGHTYEAR))
  }

  @Test
  fun `should handle mixed case platform names when filtering`() {
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

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        listOf(transaction),
        any(),
      )
    } returns metrics
    every { dailyPriceService.getLastPriceChange(testInstrument) } returns null

    val result = instrumentService.getAllInstruments(listOf("Lhv", "LIGHTYEAR"))

    expect(result).toHaveSize(1)
  }

  @Test
  fun `should filter out instruments with no transactions when empty platform list provided`() {
    val transaction =
      createBuyTransaction(quantity = BigDecimal("10"), price = BigDecimal("100"))

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)

    val result = instrumentService.getAllInstruments(emptyList())

    expect(result).toBeEmpty()
  }

  @Test
  fun `should filter out instruments when no matching platform transactions exist`() {
    val lhvTransaction =
      createBuyTransaction(
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        platform = Platform.LHV,
      )

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(lhvTransaction)

    val result = instrumentService.getAllInstruments(listOf("LIGHTYEAR"))

    expect(result).toBeEmpty()
  }

  @Test
  fun `should return only instruments with matching platforms when multiple instruments exist`() {
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

    every { instrumentRepository.findAll() } returns listOf(testInstrument, instrument2)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(tx1, tx2)
    every {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        listOf(tx1),
        any(),
      )
    } returns metrics1
    every { dailyPriceService.getLastPriceChange(testInstrument) } returns null

    val result = instrumentService.getAllInstruments(listOf("lhv"))

    expect(result).toHaveSize(1)
    expect(result[0].symbol).toEqual("AAPL")
  }

  @Test
  fun `should use current date from clock when calculating metrics`() {
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

    every { instrumentRepository.findAll() } returns listOf(testInstrument)
    every { portfolioTransactionRepository.findAllWithInstruments() } returns listOf(transaction)
    every {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        any(),
        testDate,
      )
    } returns metrics
    every { dailyPriceService.getLastPriceChange(testInstrument) } returns null

    instrumentService.getAllInstruments()

    verify {
      investmentMetricsService.calculateInstrumentMetricsWithProfits(
        testInstrument,
        any(),
        testDate,
      )
    }
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
