package ee.tenman.portfolio.controller

import ee.tenman.portfolio.dto.CalculationResult
import ee.tenman.portfolio.service.calculation.CalculationService
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/calculator")
@Validated
class CalculatorController(
  private val calculationService: CalculationService,
) {
  @GetMapping
  fun calculate(): CalculationResult = calculationService.getCalculationResult()
}
