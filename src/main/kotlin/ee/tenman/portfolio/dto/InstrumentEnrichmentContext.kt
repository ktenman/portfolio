package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.TimeRange
import java.time.LocalDate

data class InstrumentEnrichmentContext(
  val calculationDate: LocalDate,
  val priceChangePeriod: TimeRange,
  val targetPlatforms: Set<Platform>?,
)
