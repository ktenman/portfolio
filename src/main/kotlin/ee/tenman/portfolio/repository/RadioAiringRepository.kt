package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.RadioAiring
import ee.tenman.portfolio.domain.Station
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface RadioAiringRepository : JpaRepository<RadioAiring, Long> {
  fun findAllByExternalIdIn(externalIds: Collection<Long>): List<RadioAiring>

  fun findByEndsAtAfterOrderByStartsAt(after: Instant): List<RadioAiring>

  fun findByExternalId(externalId: Long): RadioAiring?

  @Query("SELECT a FROM RadioAiring a WHERE a.startsAt >= :from AND a.startsAt < :until ORDER BY a.startsAt")
  fun findStarting(
    from: Instant,
    until: Instant,
  ): List<RadioAiring>

  @Query("SELECT a FROM RadioAiring a WHERE a.startsAt < :until AND a.endsAt > :from ORDER BY a.startsAt")
  fun findOverlapping(
    from: Instant,
    until: Instant,
  ): List<RadioAiring>

  @Modifying
  @Query(
    "DELETE FROM RadioAiring a WHERE a.station = :station AND a.startsAt >= :from AND a.startsAt < :until AND a.externalId NOT IN :kept",
  )
  fun deleteDropped(
    station: Station,
    from: Instant,
    until: Instant,
    kept: Collection<Long>,
  )
}
