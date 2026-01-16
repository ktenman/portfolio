package ee.tenman.portfolio.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

data class DiversificationConfigAllocationDto(
  @field:Min(value = 0, message = "Instrument ID must be non-negative")
  val instrumentId: Long,
  @field:PositiveOrZero(message = "Value must be non-negative")
  val value: BigDecimal,
)
