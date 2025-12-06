package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PriceChangePeriod
import java.time.LocalDate

data class InstrumentEnrichmentContext(
  val calculationDate: LocalDate,
  val priceChangePeriod: PriceChangePeriod,
  val targetPlatforms: Set<Platform>?,
)
