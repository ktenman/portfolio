package ee.tenman.portfolio.dto

import java.util.UUID

data class PrefetchRequest(
  val holdingUuids: List<UUID>,
)
