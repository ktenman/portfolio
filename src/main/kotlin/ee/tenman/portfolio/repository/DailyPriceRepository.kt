package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
interface DailyPriceRepository : JpaRepository<DailyPrice, Long> {
  fun findByInstrumentAndEntryDateAndProviderName(
    instrument: Instrument,
    entryDate: LocalDate,
    providerName: ProviderName,
  ): DailyPrice?

  fun findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
    instrument: Instrument,
    startDate: LocalDate,
    endDate: LocalDate,
  ): DailyPrice?

  fun findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateAsc(
    instrument: Instrument,
    startDate: LocalDate,
    endDate: LocalDate,
  ): DailyPrice?

  fun findAllByInstrument(instrument: Instrument): List<DailyPrice>

  fun findTop10ByInstrumentOrderByEntryDateDesc(instrument: Instrument): List<DailyPrice>

  fun findAllByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
    instrument: Instrument,
    startDate: LocalDate,
    endDate: LocalDate,
  ): List<DailyPrice>

  @Query("SELECT DISTINCT dp.entryDate FROM DailyPrice dp WHERE dp.instrument = :instrument")
  fun findAllEntryDatesByInstrument(instrument: Instrument): Set<LocalDate>

  fun findByInstrumentAndEntryDate(
    instrument: Instrument,
    entryDate: LocalDate,
  ): DailyPrice?

  fun existsByInstrument(instrument: Instrument): Boolean

  @Modifying
  @Query(
    """
    INSERT INTO daily_price (instrument_id, entry_date, provider_name, open_price, high_price, low_price, close_price, volume, created_at, updated_at, version)
    VALUES (:instrumentId, :entryDate, :providerName, :openPrice, :highPrice, :lowPrice, :closePrice, :volume, NOW(), NOW(), 0)
    ON CONFLICT (instrument_id, entry_date, provider_name)
    DO UPDATE SET open_price = :openPrice, high_price = :highPrice, low_price = :lowPrice, close_price = :closePrice, volume = :volume, updated_at = NOW(), version = daily_price.version + 1
    """,
    nativeQuery = true,
  )
  fun upsert(
    instrumentId: Long,
    entryDate: LocalDate,
    providerName: String,
    openPrice: BigDecimal?,
    highPrice: BigDecimal?,
    lowPrice: BigDecimal?,
    closePrice: BigDecimal,
    volume: Long?,
  )
}
