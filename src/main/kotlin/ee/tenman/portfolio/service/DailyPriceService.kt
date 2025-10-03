package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.DailyPriceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class PriceChange(
  val changeAmount: BigDecimal,
  val changePercent: Double,
)

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

  @Transactional(readOnly = true)
  fun getLastPriceChange(instrument: Instrument): PriceChange? {
    val recentPrices = dailyPriceRepository.findTop2ByInstrumentOrderByEntryDateDesc(instrument)
    if (recentPrices.size < 2) return null

    val currentPrice = recentPrices[0].closePrice
    val previousPrice = recentPrices[1].closePrice
    val changeAmount = currentPrice.subtract(previousPrice)
    val changePercent = calculateChangePercent(changeAmount, previousPrice)

    return PriceChange(changeAmount, changePercent)
  }

  private fun calculateChangePercent(
    changeAmount: BigDecimal,
    previousPrice: BigDecimal,
  ): Double =
    changeAmount
      .divide(previousPrice, 10, RoundingMode.HALF_UP)
      .multiply(BigDecimal(100))
      .toDouble()
}
