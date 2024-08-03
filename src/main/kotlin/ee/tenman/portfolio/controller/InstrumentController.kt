package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.service.InstrumentService
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
class InstrumentController(
  private val instrumentService: InstrumentService,
) {

  @PostMapping
  @Loggable
  fun saveInstrument(@Valid @RequestBody instrumentDto: InstrumentDto): InstrumentDto {
    val savedInstrument = instrumentService.saveInstrument(instrumentDto.toEntity())
    return InstrumentDto.fromEntity(savedInstrument)
  }

  @GetMapping
  @Loggable
  fun getAllInstruments(): List<InstrumentDto> = instrumentService.getAllInstruments()
    .sortedBy { it.id }
    .map { InstrumentDto.fromEntity(it) }

  @PutMapping("/{id}")
  @Loggable
  fun updateInstrument(@PathVariable id: Long, @Valid @RequestBody instrumentDto: InstrumentDto): InstrumentDto {
    val existingInstrument = instrumentService.getInstrumentById(id)
    val updatedInstrument = existingInstrument.apply {
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
  fun deleteInstrument(@PathVariable id: Long) {
    instrumentService.deleteInstrument(id)
  }

  data class InstrumentDto(
    val id: Long? = null,

    @field:NotBlank(message = "Symbol must not be blank")
    val symbol: String,

    val name: String,

    val category: String,

    @field:NotBlank(message = "Base currency must not be blank")
    val baseCurrency: String,

    val currentPrice: BigDecimal? = null
  ) {
    fun toEntity() = Instrument(
      symbol = symbol,
      name = name,
      category = category,
      baseCurrency = baseCurrency,
      currentPrice = currentPrice
    )

    companion object {
      fun fromEntity(instrument: Instrument) = InstrumentDto(
        id = instrument.id,
        symbol = instrument.symbol,
        name = instrument.name,
        category = instrument.category,
        baseCurrency = instrument.baseCurrency,
        currentPrice = instrument.currentPrice
      )
    }
  }

}
