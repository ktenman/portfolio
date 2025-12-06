package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PriceChangePeriod
import ee.tenman.portfolio.model.PriceChange
import ee.tenman.portfolio.repository.DailyPriceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
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

  @Transactional(readOnly = true)
  fun getLastPriceChange(instrument: Instrument): PriceChange? {
    val recentPrices = dailyPriceRepository.findTop10ByInstrumentOrderByEntryDateDesc(instrument)
    if (recentPrices.isEmpty()) return null

    val currentPrice = recentPrices[0].closePrice
    val previousPrice =
      recentPrices
      .drop(1)
      .firstOrNull { it.closePrice != currentPrice }
      ?.closePrice
      ?: return null

    val changeAmount = currentPrice.subtract(previousPrice)
    val changePercent = calculateChangePercent(changeAmount, previousPrice)

    return PriceChange(changeAmount, changePercent)
  }

  @Transactional(readOnly = true)
  fun getPriceChange(
    instrument: Instrument,
    period: PriceChangePeriod = PriceChangePeriod.P24H,
  ): PriceChange? {
    val currentDate = LocalDate.now()
    val targetDate = currentDate.minusDays(period.days.toLong())

    val currentPrice = findLastDailyPrice(instrument, currentDate)?.closePrice ?: return null

    val previousPrice =
      dailyPriceRepository
        .findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
          instrument,
          targetDate.minusDays(5),
          targetDate,
        )?.closePrice ?: return null

    val changeAmount = currentPrice.subtract(previousPrice)
    val changePercent = calculateChangePercent(changeAmount, previousPrice)

    return PriceChange(changeAmount, changePercent)
  }

  @Transactional(readOnly = true)
  fun getPriceChangeSinceDate(
    instrument: Instrument,
    startDate: LocalDate,
  ): PriceChange? {
    val currentDate = LocalDate.now()

    val currentPrice = findLastDailyPrice(instrument, currentDate)?.closePrice ?: return null

    val startPrice = findPriceClosestToDate(instrument, startDate) ?: return null

    val changeAmount = currentPrice.subtract(startPrice)
    val changePercent = calculateChangePercent(changeAmount, startPrice)

    return PriceChange(changeAmount, changePercent)
  }

  private fun findPriceClosestToDate(
    instrument: Instrument,
    targetDate: LocalDate,
  ): BigDecimal? =
    dailyPriceRepository
      .findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateAsc(
        instrument,
        targetDate.minusDays(2),
        targetDate.plusDays(2),
      )?.closePrice

  private fun calculateChangePercent(
    changeAmount: BigDecimal,
    previousPrice: BigDecimal,
  ): Double =
    changeAmount
      .divide(previousPrice, 10, RoundingMode.HALF_UP)
      .multiply(BigDecimal(100))
      .toDouble()
}
