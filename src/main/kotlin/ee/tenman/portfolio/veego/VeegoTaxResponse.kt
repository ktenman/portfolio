package ee.tenman.portfolio.veego

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class VeegoTaxResponse(
  @JsonProperty("annual_tax")
  val annualTax: BigDecimal? = null,
  @JsonProperty("registration_tax")
  val registrationTax: BigDecimal? = null,
  val make: String? = null,
  val model: String? = null,
  val year: Int? = null,
  val group: String? = null,
  val co2: Int? = null,
  val fuel: String? = null,
  val weight: Int? = null,
)
