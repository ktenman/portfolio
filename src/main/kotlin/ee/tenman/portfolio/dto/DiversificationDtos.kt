package ee.tenman.portfolio.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.io.Serializable
import java.math.BigDecimal

data class AllocationDto(
  @field:Positive(message = "Instrument ID must be positive")
  val instrumentId: Long,
  @field:DecimalMin(value = "0", message = "Percentage must be non-negative")
  val percentage: BigDecimal,
)

data class DiversificationCalculatorRequestDto(
  @field:Valid
  val allocations: List<AllocationDto>,
)

data class DiversificationCalculatorResponseDto(
  val weightedTer: BigDecimal,
  val weightedAnnualReturn: BigDecimal,
  val totalUniqueHoldings: Int,
  val holdings: List<DiversificationHoldingDto>,
  val sectors: List<DiversificationSectorDto>,
  val countries: List<DiversificationCountryDto>,
  val concentration: ConcentrationDto,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}

data class DiversificationHoldingDto(
  val name: String,
  val ticker: String?,
  val percentage: BigDecimal,
  val inEtfs: String,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}

data class DiversificationSectorDto(
  val sector: String,
  val percentage: BigDecimal,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}

data class DiversificationCountryDto(
  val countryCode: String?,
  val countryName: String,
  val percentage: BigDecimal,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}

data class ConcentrationDto(
  val top10Percentage: BigDecimal,
  val largestPosition: LargestPositionDto?,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}

data class LargestPositionDto(
  val name: String,
  val percentage: BigDecimal,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}

data class DiversificationConfigDto(
  @field:Valid
  @field:NotEmpty(message = "At least one allocation is required")
  val allocations: List<DiversificationConfigAllocationDto>,
  @field:Pattern(regexp = "percentage|amount", message = "Input mode must be 'percentage' or 'amount'")
  val inputMode: String,
  val selectedPlatforms: List<String> = emptyList(),
  val optimizeEnabled: Boolean = false,
  val totalInvestment: Double = 0.0,
  @field:Pattern(regexp = "units|amount", message = "Action display mode must be 'units' or 'amount'")
  val actionDisplayMode: String = "units",
  val buyOnlyEnabled: Boolean = false,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}

data class DiversificationConfigAllocationDto(
  @field:Min(value = 0, message = "Instrument ID must be non-negative")
  val instrumentId: Long,
  @field:PositiveOrZero(message = "Value must be non-negative")
  val value: BigDecimal,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}
