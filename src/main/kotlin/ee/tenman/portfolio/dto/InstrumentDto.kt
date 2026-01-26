package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.model.InstrumentSnapshot
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "Financial instrument data transfer object")
data class InstrumentDto(
  @field:Schema(description = "Unique identifier", example = "1")
  val id: Long? = null,
  @field:Schema(description = "Ticker symbol", example = "AAPL", required = true)
  @field:NotBlank(message = "Symbol must not be blank")
  val symbol: String,
  val name: String,
  val category: String,
  @field:NotBlank(message = "Base currency must not be blank")
  val baseCurrency: String,
  val currentPrice: BigDecimal? = null,
  val quantity: BigDecimal? = BigDecimal.ZERO,
  @field:NotBlank(message = "Provider name must not be blank")
  val providerName: String,
  val totalInvestment: BigDecimal? = BigDecimal.ZERO,
  val currentValue: BigDecimal? = BigDecimal.ZERO,
  val profit: BigDecimal? = BigDecimal.ZERO,
  val realizedProfit: BigDecimal? = BigDecimal.ZERO,
  val unrealizedProfit: BigDecimal? = BigDecimal.ZERO,
  val xirr: Double? = 0.0,
  val platforms: Set<String> = emptySet(),
  val priceChangeAmount: BigDecimal? = null,
  val priceChangePercent: Double? = null,
  val ter: BigDecimal? = null,
  val xirrAnnualReturn: BigDecimal? = null,
  val firstTransactionDate: LocalDate? = null,
) {
  fun toEntity() =
    Instrument(
      symbol = symbol,
      name = name,
      category = category,
      baseCurrency = baseCurrency,
      currentPrice = currentPrice,
      providerName = ProviderName.valueOf(providerName),
    ).apply {
      this.id = id
    }

  companion object {
    fun fromEntity(instrument: Instrument) =
      InstrumentDto(
        id = instrument.id,
        symbol = instrument.symbol,
        name = instrument.name,
        category = instrument.category,
        baseCurrency = instrument.baseCurrency,
        currentPrice = instrument.currentPrice,
        quantity = BigDecimal.ZERO,
        providerName = instrument.providerName.name,
        totalInvestment = BigDecimal.ZERO,
        currentValue = BigDecimal.ZERO,
        profit = BigDecimal.ZERO,
        realizedProfit = BigDecimal.ZERO,
        unrealizedProfit = BigDecimal.ZERO,
        xirr = 0.0,
        platforms = emptySet(),
        priceChangeAmount = null,
        priceChangePercent = null,
        ter = instrument.ter,
        xirrAnnualReturn = instrument.xirrAnnualReturn,
      )

    fun fromSnapshot(snapshot: InstrumentSnapshot) =
      InstrumentDto(
        id = snapshot.instrument.id,
        symbol = snapshot.instrument.symbol,
        name = snapshot.instrument.name,
        category = snapshot.instrument.category,
        baseCurrency = snapshot.instrument.baseCurrency,
        currentPrice = snapshot.instrument.currentPrice,
        quantity = snapshot.quantity,
        providerName = snapshot.instrument.providerName.name,
        totalInvestment = snapshot.totalInvestment,
        currentValue = snapshot.currentValue,
        profit = snapshot.profit,
        realizedProfit = snapshot.realizedProfit,
        unrealizedProfit = snapshot.unrealizedProfit,
        xirr = snapshot.xirr,
        platforms = snapshot.platforms.map { it.name }.toSet(),
        priceChangeAmount = snapshot.priceChangeAmount,
        priceChangePercent = snapshot.priceChangePercent,
        ter = snapshot.instrument.ter,
        xirrAnnualReturn = snapshot.instrument.xirrAnnualReturn,
        firstTransactionDate = snapshot.firstTransactionDate,
      )
  }
}
