package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.DailyPriceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@Service
class DailyPriceService(
  private val dailyPriceRepository: DailyPriceRepository,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(readOnly = true)
  fun getPrice(
    instrument: Instrument,
    date: LocalDate,
  ): BigDecimal =
    instrument.cashPriceOrNull()
      ?: dailyPriceRepository
        .findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(instrument, date.minusYears(10), date)
        ?.closePrice
      ?: throw NoSuchElementException("No price found for ${instrument.symbol} on or before $date")

  @Transactional
  fun saveDailyPrice(dailyPrice: DailyPrice) {
    dailyPriceRepository.upsert(
      instrumentId = dailyPrice.instrument.id,
      entryDate = dailyPrice.entryDate,
      providerName = dailyPrice.providerName.name,
      openPrice = dailyPrice.openPrice,
      highPrice = dailyPrice.highPrice,
      lowPrice = dailyPrice.lowPrice,
      closePrice = dailyPrice.closePrice,
      volume = dailyPrice.volume,
    )
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
  fun findPriceNear(
    instrument: Instrument,
    targetDate: LocalDate,
    lookbackDays: Long = 5,
  ): DailyPrice? =
    dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
      instrument,
      targetDate.minusDays(lookbackDays),
      targetDate,
    )

  @Transactional(readOnly = true)
  fun findAllByInstrument(instrument: Instrument): List<DailyPrice> = dailyPriceRepository.findAllByInstrument(instrument)

  @Transactional(readOnly = true)
  fun findAllExistingDates(instrument: Instrument): Set<LocalDate> = dailyPriceRepository.findAllEntryDatesByInstrument(instrument)

  @Transactional
  fun saveDailyPriceIfNotExists(dailyPrice: DailyPrice): Boolean {
    val existing =
      dailyPriceRepository.findByInstrumentAndEntryDate(
        dailyPrice.instrument,
        dailyPrice.entryDate,
      )
    if (existing != null) return false
    return runCatching { dailyPriceRepository.save(dailyPrice) }
      .map { true }
      .getOrDefault(false)
  }

  @Transactional(readOnly = true)
  fun hasHistoricalData(instrument: Instrument): Boolean = dailyPriceRepository.existsByInstrument(instrument)

  @Transactional
  fun saveCurrentPrice(
    instrument: Instrument,
    price: BigDecimal,
    date: LocalDate,
    providerName: ProviderName,
  ) {
    val dailyPrice =
      DailyPrice(
        instrument = instrument,
        entryDate = date,
        providerName = providerName,
        openPrice = null,
        highPrice = null,
        lowPrice = null,
        closePrice = price,
        volume = null,
      )
    saveDailyPrice(dailyPrice)
  }

  @Transactional(readOnly = true)
  fun getCurrentPrice(instrument: Instrument): BigDecimal =
    instrument.cashPriceOrNull()
      ?: instrument.currentPrice?.takeIf { it > BigDecimal.ZERO }
      ?: runCatching { getPrice(instrument, LocalDate.now(clock)) }
        .onFailure { log.warn("No price found for ${instrument.symbol}, using zero") }
        .getOrDefault(BigDecimal.ZERO)
}
