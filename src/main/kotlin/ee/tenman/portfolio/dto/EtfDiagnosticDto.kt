package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.ProviderName
import java.math.BigDecimal

data class EtfDiagnosticDto(
  val instrumentId: Long,
  val symbol: String,
  val providerName: ProviderName,
  val currentPrice: BigDecimal?,
  val etfPositionCount: Int,
  val latestSnapshotDate: String?,
  val transactionCount: Int,
  val netQuantity: BigDecimal,
  val hasEtfHoldings: Boolean,
  val hasActivePosition: Boolean,
  val platforms: List<String>,
)
