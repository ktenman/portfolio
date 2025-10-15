package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class SummaryBatchProcessorService(
  private val portfolioDailySummaryRepository: PortfolioDailySummaryRepository,
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

  fun processBatch(
    batch: List<LocalDate>,
    summaryCalculator: (LocalDate) -> PortfolioDailySummary,
  ): Int {
    val summaries = mutableListOf<PortfolioDailySummary>()

    for (date in batch) {
      try {
        val summary = summaryCalculator(date)
        summaries.add(summary)
      } catch (e: Exception) {
        log.warn("Failed to calculate summary for $date: ${e.message}")
      }
    }

    return if (summaries.isNotEmpty()) {
      saveSummaries(summaries)
    } else {
      0
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun saveSummaries(summaries: List<PortfolioDailySummary>): Int =
    try {
      val saved = portfolioDailySummaryRepository.saveAll(summaries)
      log.debug("Saved ${saved.count()} summaries")
      summaries.size
    } catch (e: Exception) {
      log.error("Failed to save summaries: ${e.message}", e)
      0
    }
}
