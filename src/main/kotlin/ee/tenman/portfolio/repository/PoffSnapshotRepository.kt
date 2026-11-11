package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.PoffSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PoffSnapshotRepository : JpaRepository<PoffSnapshot, Long> {
  fun findTopByOrderByIdDesc(): PoffSnapshot?
}
