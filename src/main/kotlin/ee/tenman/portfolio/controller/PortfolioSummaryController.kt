package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.service.PortfolioSummaryService
import ee.tenman.portfolio.service.AsyncXirrCalculationService
import ee.tenman.portfolio.service.XirrCalculationRequest
import ee.tenman.portfolio.service.XirrCalculationResult
import ee.tenman.portfolio.service.AsyncJobService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/api/portfolio-summary")
class PortfolioSummaryController(
  private val portfolioSummaryService: PortfolioSummaryService,
  private val asyncXirrCalculationService: AsyncXirrCalculationService,
  private val asyncJobService: AsyncJobService
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @PostMapping("/recalculate")
  @ResponseStatus(HttpStatus.OK)
  fun recalculatePortfolioSummaries(): Map<String, Any> {
    log.info("Received request to recalculate all portfolio summaries")
    val startTime = System.currentTimeMillis()
    val count = portfolioSummaryService.recalculateAllDailySummaries()
    val duration = System.currentTimeMillis() - startTime

    return mapOf(
      "success" to true,
      "message" to "Successfully recalculated $count daily summaries",
      "count" to count,
      "durationMs" to duration
    )
  }

  @GetMapping("/historical")
  @Loggable
  fun getHistoricalPortfolioSummary(
    @RequestParam page: Int,
    @RequestParam size: Int
  ): Page<PortfolioSummaryDto> {
    val historicalSummaries = portfolioSummaryService.getAllDailySummaries(page, size)

    return historicalSummaries.map { summary ->
      PortfolioSummaryDto(
        date = summary.entryDate,
        totalValue = summary.totalValue,
        xirrAnnualReturn = summary.xirrAnnualReturn,
        totalProfit = summary.totalProfit,
        earningsPerDay = summary.earningsPerDay,
        earningsPerMonth = summary.earningsPerDay.multiply(BigDecimal(365.25 / 12))
      )
    }
  }

  @GetMapping("/current")
  @Loggable
  fun getCurrentPortfolioSummary(): PortfolioSummaryDto {
    val currentDaySummary = portfolioSummaryService.getCurrentDaySummary()

    return PortfolioSummaryDto(
      date = currentDaySummary.entryDate,
      totalValue = currentDaySummary.totalValue,
      xirrAnnualReturn = currentDaySummary.xirrAnnualReturn,
      totalProfit = currentDaySummary.totalProfit,
      earningsPerDay = currentDaySummary.earningsPerDay,
      earningsPerMonth = currentDaySummary.earningsPerDay.multiply(BigDecimal(365.25 / 12))
    )
  }

  @PostMapping("/recalculate/async")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun recalculatePortfolioSummariesAsync(
    @RequestBody(required = false) request: XirrCalculationRequest?
  ): AsyncJobResponse {
    if (asyncXirrCalculationService.hasActiveCalculations()) {
      throw IllegalStateException("XIRR calculation is already in progress")
    }

    val finalRequest = request ?: XirrCalculationRequest(recalculateAll = true)
    val job = asyncJobService.createJob(
      jobType = AsyncXirrCalculationService.JOB_TYPE_XIRR_CALCULATION,
      parameters = finalRequest
    )

    asyncXirrCalculationService.calculateXirrAsync(job.id!!, finalRequest)

    return AsyncJobResponse(
      jobId = job.id!!,
      status = job.status.name,
      message = "XIRR calculation started",
      checkStatusUrl = "/api/jobs/${job.id}"
    )
  }

  @GetMapping("/jobs/{jobId}")
  @Loggable
  fun getJobStatus(@PathVariable jobId: Long): JobStatusResponse {
    val job = asyncJobService.getJob(jobId)
      ?: throw IllegalArgumentException("Job not found: $jobId")

    return JobStatusResponse(
      jobId = job.id!!,
      jobType = job.jobType,
      status = job.status.name,
      progress = job.progress,
      totalItems = job.totalItems,
      startedAt = job.startedAt,
      completedAt = job.completedAt,
      duration = job.duration,
      result = job.result?.let { asyncJobService.parseResult(job, XirrCalculationResult::class.java) },
      errorMessage = job.errorMessage
    )
  }

  data class PortfolioSummaryDto(
    val date: LocalDate,
    val totalValue: BigDecimal,
    val xirrAnnualReturn: BigDecimal,
    val totalProfit: BigDecimal,
    val earningsPerDay: BigDecimal,
    val earningsPerMonth: BigDecimal
  )

  data class AsyncJobResponse(
    val jobId: Long,
    val status: String,
    val message: String,
    val checkStatusUrl: String
  )

  data class JobStatusResponse(
    val jobId: Long,
    val jobType: String,
    val status: String,
    val progress: Int,
    val totalItems: Int?,
    val startedAt: java.time.Instant?,
    val completedAt: java.time.Instant?,
    val duration: Long?,
    val result: Any?,
    val errorMessage: String?
  )
}
