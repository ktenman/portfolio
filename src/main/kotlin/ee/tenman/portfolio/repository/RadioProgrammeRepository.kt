package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.RadioProgramme
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RadioProgrammeRepository : JpaRepository<RadioProgramme, Long> {
  fun findByExternalId(externalId: Long): RadioProgramme?

  fun findAllByExternalIdIn(externalIds: Collection<Long>): List<RadioProgramme>
}
