package ee.tenman.portfolio.dto

import jakarta.validation.Valid

data class DiversificationCalculatorRequestDto(
  @field:Valid
  val allocations: List<AllocationDto>,
)
