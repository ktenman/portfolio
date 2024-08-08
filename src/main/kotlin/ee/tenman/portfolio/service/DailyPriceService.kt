package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.DailyPriceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class DailyPriceService(private val dailyPriceRepository: DailyPriceRepository) {

  @Transactional
  fun saveDailyPrice(dailyPrice: DailyPrice): DailyPrice {
    val existingPrice = dailyPriceRepository.findByInstrumentAndEntryDateAndProviderName(
      dailyPrice.instrument,
      dailyPrice.entryDate,
      dailyPrice.providerName
    )

    return if (existingPrice != null) {
      existingPrice.apply {
        openPrice = dailyPrice.openPrice
        highPrice = dailyPrice.highPrice
        lowPrice = dailyPrice.lowPrice
        closePrice = dailyPrice.closePrice
        volume = dailyPrice.volume
      }
      dailyPriceRepository.save(existingPrice)
    } else {
      dailyPriceRepository.save(dailyPrice)
    }
  }

  @Transactional(readOnly = true)
  fun findLastDailyPrice(instrument: Instrument): DailyPrice?
  = dailyPriceRepository.findTopByInstrumentOrderByEntryDateDesc(instrument)

  @Transactional(readOnly = true)
  fun findAllByInstrument(instrument: Instrument) : List<DailyPrice> {
    return dailyPriceRepository.findAllByInstrument(instrument)
  }

}
