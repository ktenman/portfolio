package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.DailyPrice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface DailyPriceRepository : JpaRepository<DailyPrice, Long> {
  fun findByInstrumentIdAndEntryDateBetween(instrumentId: Long, startDate: LocalDate, endDate: LocalDate): List<DailyPrice>
}
