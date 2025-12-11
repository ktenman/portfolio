package ee.tenman.portfolio.testing.fixture

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import net.datafaker.Faker
import java.math.BigDecimal
import java.time.LocalDate

object TransactionFixtures {
  private val faker = Faker()

  val DEFAULT_DATE: LocalDate = LocalDate.of(2024, 1, 15)
  val DEFAULT_COMMISSION: BigDecimal = BigDecimal("5")
  val ZERO_COMMISSION: BigDecimal = BigDecimal.ZERO

  fun createInstrument(
    symbol: String = "AAPL",
    name: String = "Apple Inc.",
    category: String = "Stock",
    baseCurrency: String = "USD",
    currentPrice: BigDecimal = BigDecimal("150.00"),
    providerName: ProviderName = ProviderName.FT,
    id: Long = 1L,
  ): Instrument =
    Instrument(
      symbol = symbol,
      name = name,
      category = category,
      baseCurrency = baseCurrency,
      currentPrice = currentPrice,
      providerName = providerName,
    ).apply { this.id = id }

  fun createRandomInstrument(): Instrument =
    createInstrument(
      symbol = faker.stock().nsdqSymbol(),
      name = faker.company().name(),
      baseCurrency = faker.money().currencyCode(),
      currentPrice = BigDecimal(faker.number().randomDouble(2, 10, 500)),
      id = faker.number().numberBetween(1L, 1000L),
    )

  fun createTransaction(
    instrument: Instrument,
    type: TransactionType,
    quantity: BigDecimal,
    price: BigDecimal,
    transactionDate: LocalDate = DEFAULT_DATE,
    platform: Platform = Platform.LHV,
    commission: BigDecimal = DEFAULT_COMMISSION,
  ): PortfolioTransaction =
    PortfolioTransaction(
      instrument = instrument,
      transactionType = type,
      quantity = quantity,
      price = price,
      transactionDate = transactionDate,
      platform = platform,
      commission = commission,
    )

  fun createBuyTransaction(
    instrument: Instrument,
    quantity: BigDecimal,
    price: BigDecimal,
    transactionDate: LocalDate = DEFAULT_DATE,
    platform: Platform = Platform.LHV,
    commission: BigDecimal = DEFAULT_COMMISSION,
  ): PortfolioTransaction = createTransaction(instrument, TransactionType.BUY, quantity, price, transactionDate, platform, commission)

  fun createSellTransaction(
    instrument: Instrument,
    quantity: BigDecimal,
    price: BigDecimal,
    transactionDate: LocalDate = DEFAULT_DATE,
    platform: Platform = Platform.LHV,
    commission: BigDecimal = DEFAULT_COMMISSION,
  ): PortfolioTransaction = createTransaction(instrument, TransactionType.SELL, quantity, price, transactionDate, platform, commission)

  fun createRandomBuyTransaction(instrument: Instrument): PortfolioTransaction =
    createBuyTransaction(
      instrument = instrument,
      quantity = BigDecimal(faker.number().randomDouble(2, 1, 100)),
      price = BigDecimal(faker.number().randomDouble(2, 10, 500)),
      transactionDate = DEFAULT_DATE.minusDays(faker.number().numberBetween(1L, 365L)),
    )

  fun createRandomSellTransaction(instrument: Instrument): PortfolioTransaction =
    createSellTransaction(
      instrument = instrument,
      quantity = BigDecimal(faker.number().randomDouble(2, 1, 50)),
      price = BigDecimal(faker.number().randomDouble(2, 10, 500)),
      transactionDate = DEFAULT_DATE.minusDays(faker.number().numberBetween(1L, 180L)),
    )
}
