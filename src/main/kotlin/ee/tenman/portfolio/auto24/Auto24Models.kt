package ee.tenman.portfolio.auto24

import java.io.Serializable

data class Auto24PriceResponse(
  val registrationNumber: String,
  val marketPrice: String?,
  val vehicleName: String?,
  val error: String?,
  val attempts: Int?,
  val durationSeconds: Double?,
)

data class CarPriceResult(
  val price: String?,
  val error: String? = null,
  val durationSeconds: Double? = null,
  val vehicleName: String? = null,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}
