package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_BREAKDOWN_CACHE
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

@Service
class EtfBreakdownJsonService(
  private val etfBreakdownService: EtfBreakdownService,
  private val etfBenchmarkService: EtfBenchmarkService,
  private val jsonMapper: JsonMapper,
) {
  @Cacheable(
    ETF_BREAKDOWN_CACHE,
    key =
      "T(java.util.Objects).hash(" +
        "#etfSymbols != null && !#etfSymbols.isEmpty() ? new java.util.TreeSet(#etfSymbols).toString() : 'all', " +
        "#platforms != null && !#platforms.isEmpty() ? new java.util.TreeSet(#platforms).toString() : 'all')",
    unless = "#result.length <= 2",
  )
  fun getHoldingsBreakdown(
    etfSymbols: List<String>?,
    platforms: List<String>?,
  ): ByteArray = jsonMapper.writeValueAsBytes(etfBreakdownService.getHoldingsBreakdown(etfSymbols, platforms))

  @Cacheable(ETF_BREAKDOWN_CACHE, key = "'benchmark-' + #symbol", unless = "#result.length <= 2")
  fun getBenchmarkHoldings(symbol: String): ByteArray = jsonMapper.writeValueAsBytes(etfBenchmarkService.getBenchmarkHoldings(symbol))
}
