package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.DailyPriceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class DailyPriceService(
  private val dailyPriceRepository: DailyPriceRepository,
) {
  @Transactional(readOnly = true)
  fun getPrice(
    instrument: Instrument,
    date: LocalDate,
  ): BigDecimal {
    val dailyPrice =
      dailyPriceRepository
        .findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
          instrument,
          date.minusYears(10),
          date,
        )
        ?: throw NoSuchElementException("No price found for ${instrument.symbol} on or before $date")
    return dailyPrice.closePrice
  }

  @Transactional
  fun saveDailyPrice(dailyPrice: DailyPrice): DailyPrice {
    val existingPrice =
      dailyPriceRepository.findByInstrumentAndEntryDateAndProviderName(
        dailyPrice.instrument,
        dailyPrice.entryDate,
        dailyPrice.providerName,
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
  fun findLastDailyPrice(
    instrument: Instrument,
    latestDate: LocalDate,
  ): DailyPrice? {
    val earliestDate = latestDate.minusYears(10)

    return dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
      instrument,
      earliestDate,
      latestDate,
    )
  }

  @Transactional(readOnly = true)
  fun findAllByInstrument(instrument: Instrument): List<DailyPrice> = dailyPriceRepository.findAllByInstrument(instrument)
}
