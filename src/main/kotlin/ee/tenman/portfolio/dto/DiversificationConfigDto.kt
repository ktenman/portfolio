package ee.tenman.portfolio.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import java.io.Serializable

data class DiversificationConfigDto(
  @field:Valid
  @field:NotEmpty(message = "At least one allocation is required")
  val allocations: List<DiversificationConfigAllocationDto>,
  @field:Pattern(regexp = "percentage|amount", message = "Input mode must be 'percentage' or 'amount'")
  val inputMode: String,
  val selectedPlatform: String? = null,
  val optimizeEnabled: Boolean = false,
  val totalInvestment: Double = 0.0,
  @field:Pattern(regexp = "units|amount", message = "Action display mode must be 'units' or 'amount'")
  val actionDisplayMode: String = "units",
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}
