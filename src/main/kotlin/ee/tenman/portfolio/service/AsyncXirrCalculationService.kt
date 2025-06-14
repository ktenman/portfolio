package ee.tenman.portfolio.service

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

data class XirrCalculationResult(
  val processedDates: Int,
  val processedInstruments: Int,
  val failedCalculations: List<String> = emptyList(),
  val duration: Long,
)

@Service
class AsyncXirrCalculationService(
  private val portfolioSummaryService: PortfolioSummaryService,
  @Qualifier("calculationDispatcher") private val calculationDispatcher: CoroutineContext,
) {
  companion object {
    private const val BATCH_SIZE = 10
  }

  suspend fun calculateBatchXirrAsync(dates: List<LocalDate>): XirrCalculationResult =
    coroutineScope {
      val startTime = System.currentTimeMillis()

      val results =
        dates
          .map { date ->
            async(calculationDispatcher) {
              runCatching {
                val summary = portfolioSummaryService.calculateSummaryForDate(date)
                portfolioSummaryService.saveDailySummary(summary)
                date to null
              }.getOrElse { exception ->
                date to "Failed for date $date: ${exception.message}"
              }
            }
          }.awaitAll()

      val failures = results.mapNotNull { it.second }
      val successCount = results.count { it.second == null }

      XirrCalculationResult(
        processedDates = successCount,
        processedInstruments = 0,
        failedCalculations = failures,
        duration = System.currentTimeMillis() - startTime,
      )
    }
}
