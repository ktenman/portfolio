package ee.tenman.portfolio.dto

import java.math.BigDecimal

data class VehicleInfoResponse(
  val plateNumber: String,
  val marketPrice: String?,
  val annualTax: BigDecimal?,
  val registrationTax: BigDecimal?,
  val make: String?,
  val model: String?,
  val year: Int?,
  val group: String?,
  val co2: Int?,
  val fuel: String?,
  val weight: Int?,
  val auto24Error: String?,
  val veegoError: String?,
  val totalDurationSeconds: Double,
  val formattedText: String,
)
