package ee.tenman.portfolio.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern

data class DiversificationConfigDto(
  @field:Valid
  @field:NotEmpty(message = "At least one allocation is required")
  val allocations: List<DiversificationConfigAllocationDto>,
  @field:Pattern(regexp = "percentage|amount", message = "Input mode must be 'percentage' or 'amount'")
  val inputMode: String,
)
