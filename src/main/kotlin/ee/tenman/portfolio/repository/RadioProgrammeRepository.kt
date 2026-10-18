package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.RadioProgramme
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RadioProgrammeRepository : JpaRepository<RadioProgramme, Long> {
  fun findByKukuId(kukuId: Long): RadioProgramme?

  fun findAllByKukuIdIn(kukuIds: Collection<Long>): List<RadioProgramme>

  fun findByRecordEveryTrue(): List<RadioProgramme>
}
