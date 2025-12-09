package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioDailySummary
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SummaryBatchProcessorService(
  private val summaryPersistenceService: SummaryPersistenceService,
  private val entityManager: EntityManager,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun processSummariesInBatches(
    dates: List<LocalDate>,
    summaryCalculator: (LocalDate) -> PortfolioDailySummary,
    batchSize: Int = 30,
  ): Int =
    dates.chunked(batchSize).sumOf { batch ->
      processBatch(batch, summaryCalculator)
    }

  private fun processBatch(
    batch: List<LocalDate>,
    summaryCalculator: (LocalDate) -> PortfolioDailySummary,
  ): Int {
    val summaries =
      batch.mapNotNull { date ->
        runCatching { summaryCalculator(date) }
          .onFailure { log.warn("Failed to calculate summary for $date: ${it.message}") }
          .getOrNull()
      }
    if (summaries.isEmpty()) return 0
    val saved = summaryPersistenceService.saveSummaries(summaries)
    entityManager.clear()
    return saved
  }
}
