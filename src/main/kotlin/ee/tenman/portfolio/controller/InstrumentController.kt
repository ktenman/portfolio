package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.InstrumentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/instruments")
@Validated
@Tag(name = "Instruments", description = "APIs for managing financial instruments")
class InstrumentController(
  private val instrumentService: InstrumentService,
) {
  @PostMapping
  @Loggable
  @Operation(summary = "Create a new instrument")
  fun saveInstrument(
    @Valid @RequestBody instrumentDto: InstrumentDto,
  ): InstrumentDto {
    val savedInstrument = instrumentService.saveInstrument(instrumentDto.toEntity())
    return InstrumentDto.fromEntity(savedInstrument)
  }

  @GetMapping
  @Loggable
  fun getAllInstruments(): List<InstrumentDto> =
    instrumentService
      .getAllInstruments()
      .sortedBy { it.id }
      .map { InstrumentDto.fromEntity(it) }

  @PutMapping("/{id}")
  @Loggable
  @Operation(summary = "Update an existing instrument")
  fun updateInstrument(
    @PathVariable id: Long,
    @Valid @RequestBody instrumentDto: InstrumentDto,
  ): InstrumentDto {
    val existingInstrument = instrumentService.getInstrumentById(id)
    val updatedInstrument =
      existingInstrument.apply {
        symbol = instrumentDto.symbol
        name = instrumentDto.name
        category = instrumentDto.category
        baseCurrency = instrumentDto.baseCurrency
        currentPrice = instrumentDto.currentPrice
      }

    val savedInstrument = instrumentService.saveInstrument(updatedInstrument)
    return InstrumentDto.fromEntity(savedInstrument)
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun deleteInstrument(
    @PathVariable id: Long,
  ) = instrumentService.deleteInstrument(id)

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
    val xirr: Double? = 0.0,
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
          xirr = instrument.xirr,
        )
    }
  }
}
