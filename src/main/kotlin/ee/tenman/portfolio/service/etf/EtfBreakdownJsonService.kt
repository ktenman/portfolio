package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_BREAKDOWN_CACHE
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.util.Objects

@Service
class EtfBreakdownJsonService(
  private val etfBreakdownService: EtfBreakdownService,
  private val etfBenchmarkService: EtfBenchmarkService,
  private val jsonMapper: JsonMapper,
) {
  @Cacheable(ETF_BREAKDOWN_CACHE, key = "#root.target.breakdownKey(#etfSymbols, #platforms)", unless = "#result.length <= 2")
  fun getHoldingsBreakdown(
    etfSymbols: List<String>?,
    platforms: List<String>?,
  ): ByteArray = jsonMapper.writeValueAsBytes(etfBreakdownService.getHoldingsBreakdown(etfSymbols, platforms))

  @Cacheable(ETF_BREAKDOWN_CACHE, key = "'benchmark-' + #symbol", unless = "#result.length <= 2")
  fun getBenchmarkHoldings(symbol: String): ByteArray = jsonMapper.writeValueAsBytes(etfBenchmarkService.getBenchmarkHoldings(symbol))

  fun breakdownKey(
    etfSymbols: List<String>?,
    platforms: List<String>?,
  ): Int = Objects.hash(sortedOrAll(etfSymbols), sortedOrAll(platforms))

  private fun sortedOrAll(values: List<String>?): String = values?.takeIf { it.isNotEmpty() }?.toSortedSet()?.toString() ?: "all"
}
