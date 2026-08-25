package ee.tenman.portfolio.controller

import ee.tenman.portfolio.dto.EtfDiagnosticDto
import ee.tenman.portfolio.dto.EtfHoldingBreakdownDto
import ee.tenman.portfolio.dto.PortfolioWarningDto
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.warning.PortfolioWarningService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/etf-breakdown")
class EtfBreakdownController(
  private val etfBreakdownService: EtfBreakdownService,
  private val portfolioWarningService: PortfolioWarningService,
) {
  @GetMapping
  fun getEtfHoldingsBreakdown(
    @RequestParam(required = false) etfSymbols: List<String>?,
    @RequestParam(required = false) platforms: List<String>?,
  ): List<EtfHoldingBreakdownDto> = etfBreakdownService.getHoldingsBreakdown(etfSymbols, platforms)

  @GetMapping("/warnings")
  fun getWarnings(
    @RequestParam(required = false) etfSymbols: List<String>?,
    @RequestParam(required = false) platforms: List<String>?,
  ): List<PortfolioWarningDto> = portfolioWarningService.getWarnings(etfSymbols, platforms)

  @DeleteMapping("/cache")
  fun evictCache() {
    etfBreakdownService.evictBreakdownCache()
  }

  @GetMapping("/diagnostic")
  fun getDiagnostic(): List<EtfDiagnosticDto> = etfBreakdownService.getDiagnosticData()
}
