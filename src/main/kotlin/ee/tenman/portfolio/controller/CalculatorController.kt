package ee.tenman.portfolio.controller

import ee.tenman.portfolio.dto.CalculationResult
import ee.tenman.portfolio.service.CalculationService
import ee.tenman.portfolio.service.SummaryService
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/calculator")
@Validated
class CalculatorController(
  private val calculationService: CalculationService,
  private val summaryService: SummaryService,
) {
  @GetMapping
  fun calculate(): CalculationResult {
    val result = calculationService.getCalculationResult()
    result.total = summaryService.getCurrentDaySummary().totalValue
    return result
  }
}
