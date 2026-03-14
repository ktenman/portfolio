package ee.tenman.portfolio.service.comparison

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.COMPARISON_CACHE
import ee.tenman.portfolio.dto.ComparisonResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class ComparisonCacheService(
  private val instrumentComparisonService: InstrumentComparisonService,
) {
  @Cacheable(
    value = [COMPARISON_CACHE],
    key = "'compare-' + #instrumentIds.sorted().toString() + '-' + #period",
  )
  fun getComparisonData(
    instrumentIds: List<Long>,
    period: String,
  ): ComparisonResponse = instrumentComparisonService.getComparisonData(instrumentIds, period)
}
