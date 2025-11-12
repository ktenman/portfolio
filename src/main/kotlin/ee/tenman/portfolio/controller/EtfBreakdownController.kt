package ee.tenman.portfolio.controller

import ee.tenman.portfolio.dto.EtfHoldingBreakdownDto
import ee.tenman.portfolio.service.EtfBreakdownService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/etf-breakdown")
class EtfBreakdownController(
  private val etfBreakdownService: EtfBreakdownService,
) {
  @GetMapping
  fun getEtfHoldingsBreakdown(
    @RequestParam(required = false) etfSymbols: List<String>?,
  ): List<EtfHoldingBreakdownDto> = etfBreakdownService.getHoldingsBreakdown(etfSymbols)

  @DeleteMapping("/cache")
  fun evictCache() {
    etfBreakdownService.evictBreakdownCache()
  }
}
