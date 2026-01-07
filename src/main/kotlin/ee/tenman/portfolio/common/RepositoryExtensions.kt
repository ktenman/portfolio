package ee.tenman.portfolio.common

import ee.tenman.portfolio.exception.EntityNotFoundException
import java.util.Optional

fun <T> Optional<T>.orThrow(
  entity: String,
  id: Any,
): T = orElseThrow { EntityNotFoundException("$entity not found with id: $id") }

fun <T> Optional<T>.orThrowByField(
  entity: String,
  field: String,
  value: Any,
): T = orElseThrow { EntityNotFoundException("$entity not found with $field: $value") }
