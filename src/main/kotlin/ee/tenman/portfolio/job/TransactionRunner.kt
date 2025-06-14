package ee.tenman.portfolio.job

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionRunner {
  @Transactional
  fun <T> runInTransaction(block: () -> T): T = block()
}
