package ee.tenman.portfolio.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Response containing instruments list with portfolio-wide XIRR")
data class InstrumentsResponse(
  @field:Schema(description = "List of instruments with individual metrics")
  val instruments: List<InstrumentDto>,
  @field:Schema(description = "Portfolio-wide XIRR calculated from aggregate cash flows", example = "0.125")
  val portfolioXirr: Double,
)
