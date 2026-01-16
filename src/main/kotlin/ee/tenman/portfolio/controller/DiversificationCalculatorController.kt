package ee.tenman.portfolio.controller

import ee.tenman.portfolio.dto.DiversificationCalculatorRequestDto
import ee.tenman.portfolio.dto.DiversificationCalculatorResponseDto
import ee.tenman.portfolio.dto.DiversificationConfigDto
import ee.tenman.portfolio.dto.EtfDetailDto
import ee.tenman.portfolio.service.diversification.DiversificationCalculatorService
import ee.tenman.portfolio.service.diversification.DiversificationConfigService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/diversification")
class DiversificationCalculatorController(
  private val diversificationCalculatorService: DiversificationCalculatorService,
  private val diversificationConfigService: DiversificationConfigService,
) {
  @PostMapping("/calculate")
  fun calculate(
    @Valid @RequestBody request: DiversificationCalculatorRequestDto,
  ): DiversificationCalculatorResponseDto = diversificationCalculatorService.calculate(request)

  @GetMapping("/available-etfs")
  fun getAvailableEtfs(): List<EtfDetailDto> = diversificationCalculatorService.getAvailableEtfs()

  @GetMapping("/config")
  fun getConfig(): ResponseEntity<DiversificationConfigDto> =
    diversificationConfigService
      .getConfig()
      ?.let { ResponseEntity.ok(it) }
      ?: ResponseEntity.noContent().build()

  @PutMapping("/config")
  fun saveConfig(
    @Valid @RequestBody config: DiversificationConfigDto,
  ): DiversificationConfigDto = diversificationConfigService.saveConfig(config)
}
