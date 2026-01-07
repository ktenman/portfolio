package ee.tenman.portfolio.common

import ee.tenman.portfolio.exception.EntityNotFoundException
import java.util.Optional

inline fun <reified T> Optional<T>.orThrow(id: Any): T = orElseThrow { EntityNotFoundException(T::class.simpleName ?: "Entity", id) }

inline fun <reified T> Optional<T>.orThrowByField(
  field: String,
  value: Any,
): T = orElseThrow { EntityNotFoundException(T::class.simpleName ?: "Entity", value, field) }

inline fun <reified T : Any> T?.orThrow(id: Any): T = this ?: throw EntityNotFoundException(T::class.simpleName ?: "Entity", id)

inline fun <reified T : Any> T?.orThrowByField(
  field: String,
  value: Any,
): T = this ?: throw EntityNotFoundException(T::class.simpleName ?: "Entity", value, field)
