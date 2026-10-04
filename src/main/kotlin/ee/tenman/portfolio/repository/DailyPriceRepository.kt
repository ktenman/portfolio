package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.DailyPricePoint
import ee.tenman.portfolio.domain.Instrument
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
interface DailyPriceRepository : JpaRepository<DailyPrice, Long> {
  fun findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
    instrument: Instrument,
    startDate: LocalDate,
    endDate: LocalDate,
  ): DailyPrice?

  fun findAllByInstrument(instrument: Instrument): List<DailyPrice>

  @Query(
    """
    SELECT new ee.tenman.portfolio.domain.DailyPricePoint(dp.instrument.id, dp.entryDate, dp.closePrice)
    FROM DailyPrice dp
    WHERE dp.instrument = :instrument AND dp.entryDate >= :entryDate
    ORDER BY dp.entryDate, dp.providerName
    """,
  )
  fun findPricePointsByInstrumentAndEntryDateGreaterThanEqual(
    instrument: Instrument,
    entryDate: LocalDate,
  ): List<DailyPricePoint>

  @Query(
    """
    SELECT new ee.tenman.portfolio.domain.DailyPricePoint(dp.instrument.id, dp.entryDate, dp.closePrice)
    FROM DailyPrice dp
    WHERE dp.instrument IN :instruments
    """,
  )
  fun findPricePointsByInstrumentIn(instruments: Collection<Instrument>): List<DailyPricePoint>

  @Query(
    """
    SELECT new ee.tenman.portfolio.domain.DailyPricePoint(i.id, latest.entryDate, latest.closePrice)
    FROM Instrument i
    JOIN LATERAL (
      SELECT dp.entryDate AS entryDate, dp.closePrice AS closePrice
      FROM DailyPrice dp
      WHERE dp.instrument = i AND dp.entryDate <= :date
      ORDER BY dp.entryDate DESC, dp.providerName DESC
      LIMIT 1
    ) latest
    WHERE i IN :instruments
    """,
  )
  fun findLatestPricePoints(
    instruments: Collection<Instrument>,
    date: LocalDate,
  ): List<DailyPricePoint>

  @Query("SELECT DISTINCT dp.entryDate FROM DailyPrice dp WHERE dp.instrument = :instrument")
  fun findAllEntryDatesByInstrument(instrument: Instrument): Set<LocalDate>

  fun findByInstrumentAndEntryDate(
    instrument: Instrument,
    entryDate: LocalDate,
  ): DailyPrice?

  fun findFirstByInstrumentAndEntryDateLessThanEqualOrderByEntryDateDesc(
    instrument: Instrument,
    entryDate: LocalDate,
  ): DailyPrice?

  fun findFirstByInstrumentOrderByEntryDateAsc(instrument: Instrument): DailyPrice?

  fun existsByInstrument(instrument: Instrument): Boolean

  @Modifying
  @Query(
    """
    WITH updated AS (
      UPDATE daily_price
      SET open_price = :openPrice, high_price = :highPrice, low_price = :lowPrice, close_price = :closePrice, volume = :volume, updated_at = NOW(), version = version + 1
      WHERE instrument_id = :instrumentId AND entry_date = :entryDate AND provider_name = :providerName
        AND (open_price, high_price, low_price, close_price, volume) IS DISTINCT FROM (
          CAST(:openPrice AS NUMERIC(22, 12)), CAST(:highPrice AS NUMERIC(22, 12)), CAST(:lowPrice AS NUMERIC(22, 12)), CAST(:closePrice AS NUMERIC(22, 12)), :volume
        )
    )
    INSERT INTO daily_price (instrument_id, entry_date, provider_name, open_price, high_price, low_price, close_price, volume, created_at, updated_at, version)
    SELECT :instrumentId, :entryDate, :providerName, :openPrice, :highPrice, :lowPrice, :closePrice, :volume, NOW(), NOW(), 0
    WHERE NOT EXISTS (SELECT 1 FROM daily_price WHERE instrument_id = :instrumentId AND entry_date = :entryDate AND provider_name = :providerName)
    ON CONFLICT (instrument_id, entry_date, provider_name) DO NOTHING
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
