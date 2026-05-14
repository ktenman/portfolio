package ee.tenman.portfolio.auto24

import java.io.Serializable

data class CarPriceResult(
  val price: String?,
  val error: String? = null,
  val durationSeconds: Double? = null,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}
