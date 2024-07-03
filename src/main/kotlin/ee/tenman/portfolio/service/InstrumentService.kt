package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.InstrumentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InstrumentService(private val instrumentRepository: InstrumentRepository) {

  @Transactional(readOnly = true)
  fun getInstrumentById(id: Long): Instrument? = instrumentRepository.findById(id).orElse(null)

  @Transactional(readOnly = true)
  fun getInstrumentBySymbol(symbol: String): Instrument? = instrumentRepository.findBySymbol(symbol)

  @Transactional
  fun saveInstrument(instrument: Instrument): Instrument = instrumentRepository.save(instrument)

  @Transactional
  fun deleteInstrument(id: Long) = instrumentRepository.deleteById(id)
}
