package ee.tenman.portfolio.lightyear

import com.fasterxml.jackson.annotation.JsonProperty
import ee.tenman.portfolio.common.DailyPriceData
import ee.tenman.portfolio.domain.Currency
import java.math.BigDecimal

data class LightyearUuidLookupResponse(
  val symbol: String,
  val uuid: String,
)

data class LightyearHoldingResponse(
  val name: String,
  val value: Double,
  val instrumentId: String?,
)

data class LightyearPriceResponse(
  val timestamp: String,
  val price: BigDecimal,
  val change: BigDecimal,
  val changePercent: BigDecimal,
  val currency: String,
)

data class LightyearFundInfoResponse(
  val ter: BigDecimal? = null,
  val aum: BigDecimal? = null,
  val aumCurrency: String? = null,
  @JsonProperty("baseCurrency")
  val fundCurrency: String? = null,
)

data class LightyearFundInfoData(
  val ter: BigDecimal?,
  val fundCurrency: Currency?,
)

data class LightyearInstrumentResponse(
  val id: String,
  val symbol: String,
  val name: String,
  val exchange: String?,
  val logo: String?,
  val summary: LightyearInstrumentSummary? = null,
)

data class LightyearInstrumentSummary(
  val sector: String? = null,
)

data class LightyearChartDataPoint(
  val timestamp: String,
  val open: BigDecimal,
  val close: BigDecimal,
  val high: BigDecimal,
  val low: BigDecimal,
  val volume: Long,
)

data class LightyearDailyPriceData(
  override val open: BigDecimal,
  override val high: BigDecimal,
  override val low: BigDecimal,
  override val close: BigDecimal,
  override val volume: Long,
) : DailyPriceData

data class BasicRowData(
  val nameParts: List<String>,
  val weightText: String,
)

data class ValidatedRowData(
  val name: String,
  val ticker: String?,
  val sector: String?,
  val weightText: String,
)
