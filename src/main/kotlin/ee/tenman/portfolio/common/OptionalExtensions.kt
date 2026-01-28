package ee.tenman.portfolio.common

import ee.tenman.portfolio.exception.EntityNotFoundException
import java.util.Optional

inline fun <reified T> Optional<T>.orNotFound(identifier: Any): T =
  orElseThrow { EntityNotFoundException("${T::class.simpleName} not found with id: $identifier") }

inline fun <reified T> Optional<T>.orNotFoundBySymbol(symbol: String): T =
  orElseThrow { EntityNotFoundException("${T::class.simpleName} not found with symbol: $symbol") }
