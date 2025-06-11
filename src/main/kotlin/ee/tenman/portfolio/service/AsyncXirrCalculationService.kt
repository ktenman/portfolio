package ee.tenman.portfolio.service

import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext

data class XirrCalculationRequest(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val instrumentIds: Set<Long>? = null,
    val recalculateAll: Boolean = false
)

data class XirrCalculationResult(
    val processedDates: Int,
    val processedInstruments: Int,
    val failedCalculations: List<String> = emptyList(),
    val duration: Long
)

@Service
class AsyncXirrCalculationService(
    private val portfolioSummaryService: PortfolioSummaryService,
    private val asyncJobService: AsyncJobService,
    @Qualifier("calculationDispatcher") private val calculationDispatcher: CoroutineContext
) {

    companion object {
        const val JOB_TYPE_XIRR_CALCULATION = "XIRR_CALCULATION"
        const val JOB_TYPE_XIRR_BATCH = "XIRR_BATCH_CALCULATION"
        private const val BATCH_SIZE = 10
    }

    @Async
    fun calculateXirrAsync(jobId: Long, request: XirrCalculationRequest): CompletableFuture<XirrCalculationResult> =
        CompletableFuture.supplyAsync {
            runBlocking {
                performXirrCalculation(jobId, request)
            }
        }

    private suspend fun performXirrCalculation(
        jobId: Long,
        request: XirrCalculationRequest
    ): XirrCalculationResult = coroutineScope {
        val startTime = System.currentTimeMillis()
        asyncJobService.markJobAsInProgress(jobId)

        runCatching {
            val dates = determineDatesToProcess(request)
            val totalItems = dates.size
            var processed = 0
            val failures = mutableListOf<String>()

            // Process dates in parallel batches
            dates.chunked(BATCH_SIZE).forEach { batch ->
                batch.map { date ->
                    async(calculationDispatcher) {
                        processDate(date, request.recalculateAll)
                    }
                }.awaitAll().forEach { result ->
                    result.fold(
                        onSuccess = { /* Success, nothing to do */ },
                        onFailure = { failures.add(it.message ?: "Unknown error") }
                    )
                }

                processed += batch.size
                asyncJobService.updateJobProgress(jobId, processed, totalItems)
            }

            XirrCalculationResult(
                processedDates = dates.size,
                processedInstruments = 0, // Updated by actual calculation
                failedCalculations = failures,
                duration = System.currentTimeMillis() - startTime
            )
        }.fold(
            onSuccess = { result ->
                asyncJobService.markJobAsCompleted(jobId, result)
                result
            },
            onFailure = { exception ->
                asyncJobService.markJobAsFailed(jobId, exception.message ?: "Unknown error")
                throw exception
            }
        )
    }

    private suspend fun processDate(date: LocalDate, recalculateAll: Boolean): Result<Unit> =
        runCatching {
            // For now, we'll use calculateSummaryForDate which exists
            // In the future, we can add specific recalculate logic if needed
            val summary = portfolioSummaryService.calculateSummaryForDate(date)
            portfolioSummaryService.saveDailySummary(summary)
        }

    private fun determineDatesToProcess(request: XirrCalculationRequest): List<LocalDate> {
        val endDate = request.endDate ?: LocalDate.now()
        val startDate = request.startDate ?: endDate.minusYears(5)

        return generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .toList()
    }

    fun hasActiveCalculations(): Boolean =
        asyncJobService.hasActiveJobs(JOB_TYPE_XIRR_CALCULATION) ||
                asyncJobService.hasActiveJobs(JOB_TYPE_XIRR_BATCH)

    suspend fun calculateBatchXirrAsync(dates: List<LocalDate>): XirrCalculationResult = coroutineScope {
        val startTime = System.currentTimeMillis()
        
        val results = dates.map { date ->
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
            duration = System.currentTimeMillis() - startTime
        )
    }
}