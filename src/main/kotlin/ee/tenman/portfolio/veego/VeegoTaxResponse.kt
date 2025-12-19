package ee.tenman.portfolio.veego

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class VeegoTaxResponse(
  @JsonProperty("annual_tax")
  val annualTax: BigDecimal,
  @JsonProperty("registration_tax")
  val registrationTax: BigDecimal,
  val make: String,
  val model: String,
  val year: Int,
  val group: String,
  val co2: Int,
  val fuel: String,
  val weight: Int,
)
