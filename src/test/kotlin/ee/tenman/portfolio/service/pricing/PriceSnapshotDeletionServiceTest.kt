package ee.tenman.portfolio.service.pricing

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.repository.PriceSnapshotRepository
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class PriceSnapshotDeletionServiceTest {
  private val priceSnapshotRepository = mockk<PriceSnapshotRepository>()
  private val service = PriceSnapshotDeletionService(priceSnapshotRepository)

  @BeforeEach
  fun setUp() {
    clearMocks(priceSnapshotRepository)
  }

  @Test
  fun `should delegate batch delete to repository`() {
    val cutoff = Instant.parse("2023-12-16T00:00:00Z")
    every { priceSnapshotRepository.deleteBatchOlderThan(cutoff, 1000) } returns 42

    val result = service.deleteBatch(cutoff)

    expect(result).toEqual(42)
    verify(exactly = 1) { priceSnapshotRepository.deleteBatchOlderThan(cutoff, 1000) }
  }
}
