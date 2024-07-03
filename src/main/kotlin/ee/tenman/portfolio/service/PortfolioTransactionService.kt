package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class PortfolioTransactionService(private val portfolioTransactionRepository: PortfolioTransactionRepository) {

  @Transactional(readOnly = true)
  fun getTransactionById(id: Long): PortfolioTransaction? = portfolioTransactionRepository.findById(id).orElse(null)

  @Transactional(readOnly = true)
  fun getTransactionsByInstrumentAndDateRange(instrumentId: Long, startDate: LocalDate, endDate: LocalDate): List<PortfolioTransaction> =
    portfolioTransactionRepository.findByInstrumentIdAndTransactionDateBetween(instrumentId, startDate, endDate)

  @Transactional
  fun saveTransaction(transaction: PortfolioTransaction): PortfolioTransaction = portfolioTransactionRepository.save(transaction)

  @Transactional
  fun deleteTransaction(id: Long) = portfolioTransactionRepository.deleteById(id)

  @Transactional(readOnly = true)
  fun getAllTransactions(): List<PortfolioTransaction> = portfolioTransactionRepository.findAll()
}
