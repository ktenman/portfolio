package ee.tenman.portfolio.controller

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.service.InstrumentService
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/instruments")
@Validated
class InstrumentController(private val instrumentService: InstrumentService) {

  @PostMapping
  fun saveInstrument(@Valid @RequestBody instrumentDto: InstrumentDto): InstrumentDto {
    val savedInstrument = instrumentService.saveInstrument(instrumentDto.toEntity())
    return InstrumentDto.fromEntity(savedInstrument)
  }

  @GetMapping
  fun getAllInstruments(): List<InstrumentDto> {
    val instruments = instrumentService.getAllInstruments()
    return instruments.map { InstrumentDto.fromEntity(it) }
  }

  data class InstrumentDto(
    val id: Long? = null,

    @field:NotBlank(message = "Symbol must not be blank")
    val symbol: String,

    @field:NotBlank(message = "Name must not be blank")
    val name: String,

    @field:NotBlank(message = "Category must not be blank")
    val category: String
  ) {
    fun toEntity() = Instrument(symbol = symbol, name = name, category = category)

    companion object {
      fun fromEntity(instrument: Instrument) = InstrumentDto(
        id = instrument.id,
        symbol = instrument.symbol,
        name = instrument.name,
        category = instrument.category
      )
    }
  }

  @ExceptionHandler(ConstraintViolationException::class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  fun handleValidationExceptions(exception: ConstraintViolationException): Map<String, String> {
    val errors = mutableMapOf<String, String>()
    exception.constraintViolations.forEach { violation ->
      errors[violation.propertyPath.toString()] = violation.message
    }
    return errors
  }
}
