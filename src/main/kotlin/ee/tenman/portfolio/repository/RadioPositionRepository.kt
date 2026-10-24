package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.RadioPosition
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RadioPositionRepository : JpaRepository<RadioPosition, Long> {
  fun findByListenerAndRecording(
    listener: String,
    recording: String,
  ): RadioPosition?

  @Modifying
  @Query(
    """
    INSERT INTO radio_position (listener, recording, seconds)
    VALUES (:listener, :recording, :seconds)
    ON CONFLICT (listener, recording)
    DO UPDATE SET seconds = :seconds, updated_at = NOW(), version = radio_position.version + 1
    """,
    nativeQuery = true,
  )
  fun upsert(
    listener: String,
    recording: String,
    seconds: Double,
  )
}
