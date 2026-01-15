package ee.tenman.portfolio.dto

import java.io.Serializable
import java.math.BigDecimal

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
