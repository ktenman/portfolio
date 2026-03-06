package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import ee.tenman.portfolio.dto.ReturnPredictionDto
import ee.tenman.portfolio.service.prediction.ReturnPredictionService
import ee.tenman.portfolio.service.summary.PortfolioSummaryMappingService
import ee.tenman.portfolio.service.summary.SummaryService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/portfolio-summary")
class PortfolioSummaryController(
  private val summaryService: SummaryService,
  private val summaryMappingService: PortfolioSummaryMappingService,
  private val returnPredictionService: ReturnPredictionService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @PostMapping("/recalculate")
  @ResponseStatus(HttpStatus.OK)
  @Loggable
  fun recalculatePortfolioSummaries(): Map<String, Any> {
    log.info("Received request to recalculate all portfolio summaries")
    val startTime = System.currentTimeMillis()
    val count = summaryService.recalculateAllDailySummaries()
    val duration = System.currentTimeMillis() - startTime
    return mapOf(
      "success" to true,
      "message" to "Successfully recalculated $count daily summaries",
      "count" to count,
      "durationMs" to duration,
    )
  }

  @GetMapping("/historical")
  @Loggable
  fun getHistoricalPortfolioSummary(
    @RequestParam page: Int,
    @RequestParam size: Int,
    @RequestParam(required = false) platforms: List<String>?,
  ): Page<PortfolioSummaryDto> = summaryMappingService.getHistoricalSummariesDto(page, size, Platform.parseList(platforms))

  @GetMapping("/predictions")
  @Loggable
  fun getReturnPredictions(): ReturnPredictionDto = returnPredictionService.predict()

  @GetMapping("/current")
  @Loggable
  fun getCurrentPortfolioSummary(
    @RequestParam(required = false) platforms: List<String>?,
  ): PortfolioSummaryDto = summaryMappingService.getCurrentSummaryDto(Platform.parseList(platforms))
}
