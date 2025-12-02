package ee.tenman.portfolio.domain

enum class PriceChangePeriod(
  val days: Int,
) {
  P24H(1),
  P48H(2),
  P3D(3),
  P7D(7),
  P10D(10),
  P30D(30),
  P1Y(365),
  ;

  companion object {
    fun fromString(value: String): PriceChangePeriod =
      when (value.lowercase()) {
        "24h", "p24h" -> P24H
        "48h", "p48h" -> P48H
        "3d", "p3d" -> P3D
        "7d", "p7d" -> P7D
        "10d", "p10d" -> P10D
        "30d", "p30d" -> P30D
        "1y", "p1y" -> P1Y
        else -> P24H
      }
  }
}
