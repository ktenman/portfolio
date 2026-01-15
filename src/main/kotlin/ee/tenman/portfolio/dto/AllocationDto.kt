package ee.tenman.portfolio.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class AllocationDto(
  @field:Positive(message = "Instrument ID must be positive")
  val instrumentId: Long,
  @field:DecimalMin(value = "0", message = "Percentage must be non-negative")
  val percentage: BigDecimal,
)
