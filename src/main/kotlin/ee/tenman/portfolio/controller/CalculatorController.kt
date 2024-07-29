package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.service.CalculatorService
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/calculator")
@Validated
class CalculatorController(
  private val calculatorService: CalculatorService,
) {

  @GetMapping
  @Loggable
  fun calculateXirr(): BigDecimal {
    return calculatorService.calculateXirr()
  }

}
