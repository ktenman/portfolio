package ee.tenman.portfolio.service.transaction

import ee.tenman.portfolio.common.orNull
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.ProfitCalculationEngine
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class TransactionProfitService(
  private val instrumentRepository: InstrumentRepository,
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val profitCalculationEngine: ProfitCalculationEngine,
) {
  @Transactional
  fun recalculateProfitsForInstrument(instrumentId: Long) {
    val instrument = instrumentRepository.findById(instrumentId).orNull() ?: return
    val transactions = portfolioTransactionRepository.findAllByInstrumentId(instrumentId)
    if (transactions.isEmpty()) return
    transactions.forEach { it.instrument = instrument }
    transactions
      .groupBy { it.platform }
      .forEach { (_, platformTransactions) ->
        profitCalculationEngine.calculateProfitsForPlatform(platformTransactions, BigDecimal.ZERO)
      }
    portfolioTransactionRepository.saveAll(transactions)
  }
}
