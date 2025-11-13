package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
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
}
