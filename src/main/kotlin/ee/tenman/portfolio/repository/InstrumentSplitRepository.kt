package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.InstrumentSplit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface InstrumentSplitRepository : JpaRepository<InstrumentSplit, Long> {
  @Query("SELECT s FROM InstrumentSplit s JOIN FETCH s.instrument WHERE s.effectiveDate >= :today ORDER BY s.effectiveDate, s.id")
  fun findPending(today: LocalDate): List<InstrumentSplit>
}
