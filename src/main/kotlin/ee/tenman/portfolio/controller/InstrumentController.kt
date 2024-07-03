package ee.tenman.portfolio.controller

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.service.InstrumentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/instruments")
class InstrumentController(private val instrumentService: InstrumentService) {

  @PostMapping
  fun saveInstrument(@RequestBody instrumentDto: InstrumentDto): ResponseEntity<InstrumentDto> {
    val savedInstrument = instrumentService.saveInstrument(instrumentDto.toEntity())
    return ResponseEntity.ok(InstrumentDto.fromEntity(savedInstrument))
  }

  @GetMapping
  fun getAllInstruments(): ResponseEntity<List<InstrumentDto>> {
    val instruments = instrumentService.getAllInstruments()
    return ResponseEntity.ok(instruments.map { InstrumentDto.fromEntity(it) })
  }

  data class InstrumentDto(
    val id: Long? = null,
    val symbol: String,
    val name: String,
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
}
