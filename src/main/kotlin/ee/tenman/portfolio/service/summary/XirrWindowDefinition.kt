package ee.tenman.portfolio.service.summary

import java.time.Period

data class XirrWindowDefinition(
  val label: String,
  val length: Period,
)
