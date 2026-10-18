package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.RadioAiring
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface RadioAiringRepository : JpaRepository<RadioAiring, Long> {
  fun findAllByKukuIdIn(kukuIds: Collection<Long>): List<RadioAiring>

  fun findByEndsAtAfterOrderByStartsAt(after: Instant): List<RadioAiring>

  fun findByKukuId(kukuId: Long): RadioAiring?

  @Query("SELECT a FROM RadioAiring a WHERE a.startsAt >= :from AND a.startsAt < :until ORDER BY a.startsAt")
  fun findStarting(
    from: Instant,
    until: Instant,
  ): List<RadioAiring>

  @Modifying
  @Query("DELETE FROM RadioAiring a WHERE a.startsAt >= :from AND a.startsAt < :until AND a.kukuId NOT IN :kept")
  fun deleteDropped(
    from: Instant,
    until: Instant,
    kept: Collection<Long>,
  )
}
