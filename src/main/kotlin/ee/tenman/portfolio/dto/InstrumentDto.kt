package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

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
      id.let { this.id = it }
      totalInvestment = this@InstrumentDto.totalInvestment ?: BigDecimal.ZERO
      currentValue = this@InstrumentDto.currentValue ?: BigDecimal.ZERO
      profit = this@InstrumentDto.profit ?: BigDecimal.ZERO
      realizedProfit = this@InstrumentDto.realizedProfit ?: BigDecimal.ZERO
      unrealizedProfit = this@InstrumentDto.unrealizedProfit ?: BigDecimal.ZERO
      xirr = this@InstrumentDto.xirr ?: 0.0
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
        quantity = instrument.quantity,
        providerName = instrument.providerName.name,
        totalInvestment = instrument.totalInvestment,
        currentValue = instrument.currentValue,
        profit = instrument.profit,
        realizedProfit = instrument.realizedProfit,
        unrealizedProfit = instrument.unrealizedProfit,
        xirr = instrument.xirr,
        platforms = instrument.platforms?.map { it.name }?.toSet() ?: emptySet(),
        priceChangeAmount = instrument.priceChangeAmount,
        priceChangePercent = instrument.priceChangePercent,
      )
  }
}
