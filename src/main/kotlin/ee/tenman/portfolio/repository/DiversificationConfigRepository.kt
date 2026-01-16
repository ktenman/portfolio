package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.DiversificationConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface DiversificationConfigRepository : JpaRepository<DiversificationConfig, Long> {
  @Query("SELECT d FROM DiversificationConfig d ORDER BY d.id LIMIT 1")
  fun findConfig(): DiversificationConfig?
}
