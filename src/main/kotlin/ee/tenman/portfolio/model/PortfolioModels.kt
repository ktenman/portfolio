package ee.tenman.portfolio.model

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import java.math.BigDecimal
import java.time.LocalDate

data class InstrumentSnapshot(
  val instrument: Instrument,
  val totalInvestment: BigDecimal = BigDecimal.ZERO,
  val currentValue: BigDecimal = BigDecimal.ZERO,
  val profit: BigDecimal = BigDecimal.ZERO,
  val realizedProfit: BigDecimal = BigDecimal.ZERO,
  val unrealizedProfit: BigDecimal = BigDecimal.ZERO,
  val xirr: Double? = null,
  val quantity: BigDecimal = BigDecimal.ZERO,
  val platforms: Set<Platform> = emptySet(),
  val priceChangeAmount: BigDecimal? = null,
  val priceChangePercent: Double? = null,
  val firstTransactionDate: LocalDate? = null,
)

data class InstrumentSnapshotsWithPortfolioXirr(
  val snapshots: List<InstrumentSnapshot>,
  val portfolioXirr: Double?,
)

data class XirrCalculationResult(
  val processedDates: Int,
  val processedInstruments: Int,
  val failedCalculations: List<String> = emptyList(),
  val duration: Long,
)

data class PriceChange(
  val changeAmount: BigDecimal,
  val changePercent: Double,
)

data class TransactionState(
  val totalCost: BigDecimal,
  val currentQuantity: BigDecimal,
)

enum class ProcessResult {
  SUCCESS_WITH_DAILY_PRICE,
  SUCCESS_WITHOUT_DAILY_PRICE,
  FAILED,
}

data class ReconciliationResult(
  val mergedGroups: Int,
  val mergedDuplicates: Int,
)

data class HoldingMergePlan(
  val canonicalId: Long,
  val canonicalName: String,
  val duplicateIds: List<Long>,
)

data class ClassificationResult(
  val success: Int,
  val failure: Int,
  val skipped: Int,
) {
  fun requireAnySuccess(domain: String) {
    check(success > 0 || failure == 0) { "$domain classification failed for all $failure holdings" }
  }
}
