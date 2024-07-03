package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.repository.DailyPriceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class DailyPriceService(private val dailyPriceRepository: DailyPriceRepository) {

  @Transactional(readOnly = true)
  fun getDailyPriceById(id: Long): DailyPrice? = dailyPriceRepository.findById(id).orElse(null)

  @Transactional(readOnly = true)
  fun getDailyPricesByInstrumentAndDateRange(instrumentId: Long, startDate: LocalDate, endDate: LocalDate): List<DailyPrice> =
    dailyPriceRepository.findByInstrumentIdAndEntryDateBetween(instrumentId, startDate, endDate)

  @Transactional
  fun saveDailyPrice(dailyPrice: DailyPrice): DailyPrice = dailyPriceRepository.save(dailyPrice)

  @Transactional
  fun deleteDailyPrice(id: Long) = dailyPriceRepository.deleteById(id)
}
