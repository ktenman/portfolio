package ee.tenman.portfolio.service.transaction

import ee.tenman.portfolio.domain.Platform
import java.math.BigDecimal

data class InstrumentTransactionData(
  val netQuantity: BigDecimal,
  val platforms: Set<Platform>,
  val quantityByPlatform: Map<Platform, BigDecimal> = emptyMap(),
) {
  fun quantityForPlatforms(filter: Set<Platform>?): BigDecimal {
    if (filter == null) return netQuantity
    return filter
      .mapNotNull { quantityByPlatform[it] }
      .fold(BigDecimal.ZERO) { acc, qty -> acc.add(qty) }
  }

  fun platformsForFilter(filter: Set<Platform>?): Set<Platform> {
    if (filter == null) return platforms
    return platforms.filter { it in filter }.toSet()
  }
}
