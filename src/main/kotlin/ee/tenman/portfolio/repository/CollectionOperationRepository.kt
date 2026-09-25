package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.CollectionOperation
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface CollectionOperationRepository : JpaRepository<CollectionOperation, CollectionKey> {
  @Modifying
  @Query(
    """
    INSERT INTO collection_operation (collection_key, initialized_at)
    VALUES (:key, :initializedAt)
    ON CONFLICT (collection_key) DO NOTHING
    """,
    nativeQuery = true,
  )
  fun insertIfMissing(
    key: String,
    initializedAt: Instant,
  ): Int

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT operation FROM CollectionOperation operation WHERE operation.key = :key")
  fun findLockedByKey(key: CollectionKey): CollectionOperation?
}
