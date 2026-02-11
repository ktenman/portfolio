package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.repository.PriceSnapshotRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PriceSnapshotDeletionService(
  private val priceSnapshotRepository: PriceSnapshotRepository,
) {
  companion object {
    const val DELETE_BATCH_SIZE = 1000
  }

  @Transactional
  fun deleteBatch(cutoff: Instant): Int = priceSnapshotRepository.deleteBatchOlderThan(cutoff, DELETE_BATCH_SIZE)
}
