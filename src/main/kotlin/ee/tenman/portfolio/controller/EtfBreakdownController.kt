package ee.tenman.portfolio.controller

import ee.tenman.portfolio.dto.EtfHoldingBreakdownDto
import ee.tenman.portfolio.service.EtfBreakdownService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/etf-breakdown")
class EtfBreakdownController(
  private val etfBreakdownService: EtfBreakdownService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @GetMapping
  fun getEtfHoldingsBreakdown(
    @RequestParam(required = false) etfSymbols: List<String>?,
  ): List<EtfHoldingBreakdownDto> {
    log.info("Received ETF breakdown request with etfSymbols: $etfSymbols")
    return etfBreakdownService.getHoldingsBreakdown(etfSymbols)
  }
}
