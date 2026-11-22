package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.UserPreference
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserPreferenceRepository : JpaRepository<UserPreference, Long> {
  fun findByUserId(userId: String): UserPreference?

  @Modifying
  @Query(
    """
    INSERT INTO user_preference (user_id, theme)
    VALUES (:userId, :theme)
    ON CONFLICT (user_id)
    DO UPDATE SET theme = EXCLUDED.theme,
      updated_at = NOW(), version = user_preference.version + 1
    """,
    nativeQuery = true,
  )
  fun upsertTheme(
    userId: String,
    theme: String,
  )
}
