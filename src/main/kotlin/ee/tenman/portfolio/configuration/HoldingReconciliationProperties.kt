package ee.tenman.portfolio.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "etf.holding-reconciliation")
data class HoldingReconciliationProperties(
  val enabled: Boolean = false,
  val dryRun: Boolean = true,
)
