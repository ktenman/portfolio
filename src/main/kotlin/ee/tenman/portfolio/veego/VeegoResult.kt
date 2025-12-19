package ee.tenman.portfolio.veego

import java.math.BigDecimal

data class VeegoResult(
  val annualTax: BigDecimal? = null,
  val registrationTax: BigDecimal? = null,
  val make: String? = null,
  val model: String? = null,
  val year: Int? = null,
  val group: String? = null,
  val co2: Int? = null,
  val fuel: String? = null,
  val weight: Int? = null,
  val error: String? = null,
  val durationSeconds: Double? = null,
) {
  companion object {
    fun fromResponse(
      response: VeegoTaxResponse,
      durationSeconds: Double,
    ): VeegoResult =
      VeegoResult(
        annualTax = response.annualTax,
        registrationTax = response.registrationTax,
        make = response.make,
        model = response.model,
        year = response.year,
        group = response.group,
        co2 = response.co2,
        fuel = response.fuel,
        weight = response.weight,
        durationSeconds = durationSeconds,
      )

    fun error(
      message: String,
      durationSeconds: Double,
    ): VeegoResult = VeegoResult(error = message, durationSeconds = durationSeconds)
  }
}
