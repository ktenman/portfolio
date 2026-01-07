package ee.tenman.portfolio.exception

class EntityNotFoundException(
  val entityType: String,
  val identifier: Any,
  val field: String = "id",
) : RuntimeException("$entityType not found with $field: $identifier") {
  constructor(message: String) : this(
    entityType = "Entity",
    identifier = "unknown",
    field = "id",
  ) {
    initCause(RuntimeException(message))
  }
}
