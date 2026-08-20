package ee.tenman.portfolio.service.transaction

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.ProfitCalculationEngine
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

abstract class TransactionServiceTestBase {
  protected val clock: Clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"))
  protected val testDate: LocalDate = TransactionFixtures.DEFAULT_DATE
  protected val portfolioTransactionRepository = mockk<PortfolioTransactionRepository>()
  protected val transactionCacheService = mockk<TransactionCacheService>()
  protected val testInstrument: Instrument = TransactionFixtures.createInstrument()
  protected val transactionService =
    TransactionService(portfolioTransactionRepository, ProfitCalculationEngine(), transactionCacheService, clock)

  protected fun createBuyCashFlow(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = testDate,
    commission: BigDecimal = TransactionFixtures.DEFAULT_COMMISSION,
    platform: Platform = Platform.LHV,
    instrument: Instrument = testInstrument,
  ): PortfolioTransaction = TransactionFixtures.createBuyTransaction(instrument, quantity, price, date, platform, commission)

  protected fun createSellCashFlow(
    quantity: BigDecimal,
    price: BigDecimal,
    date: LocalDate = testDate,
    commission: BigDecimal = TransactionFixtures.DEFAULT_COMMISSION,
    platform: Platform = Platform.LHV,
  ): PortfolioTransaction = TransactionFixtures.createSellTransaction(testInstrument, quantity, price, date, platform, commission)
}
