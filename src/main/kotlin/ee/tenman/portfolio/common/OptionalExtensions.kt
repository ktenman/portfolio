package ee.tenman.portfolio.common

import ee.tenman.portfolio.exception.EntityNotFoundException
import java.util.Optional

fun <T> Optional<T>.orNotFound(
  entityName: String,
  identifier: Any,
): T = orElseThrow { EntityNotFoundException("$entityName not found with id: $identifier") }

fun <T> Optional<T>.orNotFoundBySymbol(
  entityName: String,
  symbol: String,
): T = orElseThrow { EntityNotFoundException("$entityName not found with symbol: $symbol") }
