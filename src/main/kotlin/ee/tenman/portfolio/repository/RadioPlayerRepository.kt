package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.RadioPlayer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RadioPlayerRepository : JpaRepository<RadioPlayer, Long> {
  fun findByListener(listener: String): RadioPlayer?

  @Modifying
  @Query(
    """
    INSERT INTO radio_player (listener, recording, station)
    VALUES (:listener, CAST(:recording AS TEXT), CAST(:station AS VARCHAR))
    ON CONFLICT (listener)
    DO UPDATE SET recording = EXCLUDED.recording, station = EXCLUDED.station,
      updated_at = NOW(), version = radio_player.version + 1
    """,
    nativeQuery = true,
  )
  fun upsertChoice(
    listener: String,
    recording: String?,
    station: String?,
  )

  @Modifying
  @Query(
    """
    INSERT INTO radio_player (listener, x, y)
    VALUES (:listener, :x, :y)
    ON CONFLICT (listener)
    DO UPDATE SET x = EXCLUDED.x, y = EXCLUDED.y,
      updated_at = NOW(), version = radio_player.version + 1
    """,
    nativeQuery = true,
  )
  fun upsertSpot(
    listener: String,
    x: Double,
    y: Double,
  )
}
