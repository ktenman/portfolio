package ee.tenman.portfolio.common

import java.math.BigDecimal
import java.math.RoundingMode

private val HUNDRED = BigDecimal(100)

fun BigDecimal.percentOf(
  basis: BigDecimal,
  scale: Int,
): BigDecimal = divide(basis, scale, RoundingMode.HALF_UP).multiply(HUNDRED)
