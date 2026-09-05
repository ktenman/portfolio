package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.SectorSource
import java.io.Serializable
import java.math.BigDecimal
import java.util.UUID

data class EtfDetailDto(
  val instrumentId: Long,
  val symbol: String,
  val name: String,
  val allocation: BigDecimal,
  val ter: BigDecimal?,
  val annualReturn: BigDecimal?,
  val currentPrice: BigDecimal?,
  val fundCurrency: Currency? = null,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}

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

data class EtfHoldingBreakdownDto(
  val holdingUuid: UUID?,
  val holdingTicker: String?,
  val holdingName: String,
  val percentageOfTotal: BigDecimal,
  val totalValueEur: BigDecimal,
  val holdingSector: String?,
  val holdingIndustry: String?,
  val holdingGicsSector: String?,
  val holdingCountryCode: String?,
  val holdingCountryName: String?,
  val inEtfs: String,
  val numEtfs: Int,
  val platforms: String,
) : Serializable {
  companion object {
    private const val serialVersionUID = 5L
  }
}

data class HoldingData(
  val name: String,
  val ticker: String?,
  val sector: String?,
  val weight: BigDecimal,
  val rank: Int,
  val logoUrl: String? = null,
  val countryCode: String? = null,
  val countryName: String? = null,
  val sectorSource: SectorSource? = null,
)
