package ee.tenman.portfolio.common

import ee.tenman.portfolio.exception.EntityNotFoundException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Optional

private val HUNDRED = BigDecimal(100)

fun BigDecimal.percentOf(
  basis: BigDecimal,
  scale: Int,
): BigDecimal = divide(basis, scale, RoundingMode.HALF_UP).multiply(HUNDRED)

inline fun <reified T> Optional<T>.orNotFound(identifier: Any): T =
  orElseThrow { EntityNotFoundException("${T::class.simpleName} not found with id: $identifier") }

inline fun <reified T> Optional<T>.orNotFoundBySymbol(symbol: String): T =
  orElseThrow { EntityNotFoundException("${T::class.simpleName} not found with symbol: $symbol") }

fun <T> Optional<T>.orNull(): T? = orElse(null)
