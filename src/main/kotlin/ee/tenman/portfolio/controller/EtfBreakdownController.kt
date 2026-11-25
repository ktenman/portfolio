package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.SingleFlight
import ee.tenman.portfolio.dto.EtfDiagnosticDto
import ee.tenman.portfolio.service.etf.EtfBreakdownJsonService
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/etf-breakdown")
class EtfBreakdownController(
  private val etfBreakdownService: EtfBreakdownService,
  private val etfBreakdownJsonService: EtfBreakdownJsonService,
) {
  @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
  @SingleFlight
  fun getEtfHoldingsBreakdown(
    @RequestParam(required = false) etfSymbols: List<String>?,
    @RequestParam(required = false) platforms: List<String>?,
  ): ByteArray = etfBreakdownJsonService.getHoldingsBreakdown(etfSymbols, platforms)

  @GetMapping("/benchmark", produces = [MediaType.APPLICATION_JSON_VALUE])
  @SingleFlight
  fun getBenchmarkHoldings(
    @RequestParam symbol: String,
  ): ByteArray = etfBreakdownJsonService.getBenchmarkHoldings(symbol)

  @DeleteMapping("/cache")
  fun evictCache() {
    etfBreakdownService.evictBreakdownCache()
  }

  @GetMapping("/diagnostic")
  fun getDiagnostic(): List<EtfDiagnosticDto> = etfBreakdownService.getDiagnosticData()
}
