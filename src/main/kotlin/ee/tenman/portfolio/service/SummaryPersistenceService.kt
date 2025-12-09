package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class SummaryPersistenceService(
  private val portfolioDailySummaryRepository: PortfolioDailySummaryRepository,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun saveSummaries(summaries: List<PortfolioDailySummary>): Int {
    if (summaries.isEmpty()) return 0
    val saved = portfolioDailySummaryRepository.saveAll(summaries).toList()
    log.debug("Saved ${saved.size} summaries")
    return saved.size
  }
}
