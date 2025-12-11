package ee.tenman.portfolio.usecase

import ee.tenman.portfolio.model.metrics.InstrumentMetrics
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.InvestmentMetricsService
import ee.tenman.portfolio.service.transaction.TransactionService
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class GetPortfolioPerformanceUseCase(
  private val transactionRepository: PortfolioTransactionRepository,
  private val transactionService: TransactionService,
  private val investmentMetricsService: InvestmentMetricsService,
  private val clock: Clock,
) {
  operator fun invoke(instrumentId: Long): InstrumentMetrics {
    val transactions = transactionRepository.findAllByInstrumentId(instrumentId)
    if (transactions.isEmpty()) return InstrumentMetrics.EMPTY
    val instrument = transactions.first().instrument
    transactionService.calculateTransactionProfits(transactions)
    return investmentMetricsService.calculateInstrumentMetrics(instrument, transactions, LocalDate.now(clock))
  }
}
