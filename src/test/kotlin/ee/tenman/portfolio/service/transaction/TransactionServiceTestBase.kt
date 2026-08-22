package ee.tenman.portfolio.service.transaction

import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.ProfitCalculationEngine
import ee.tenman.portfolio.testing.fixture.CashFlowTestBase
import io.mockk.mockk

abstract class TransactionServiceTestBase : CashFlowTestBase() {
  protected val portfolioTransactionRepository = mockk<PortfolioTransactionRepository>()
  protected val transactionCacheService = mockk<TransactionCacheService>()
  protected val transactionService =
    TransactionService(portfolioTransactionRepository, ProfitCalculationEngine(), transactionCacheService, clock)
}
