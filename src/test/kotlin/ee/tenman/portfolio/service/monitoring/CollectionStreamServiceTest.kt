package ee.tenman.portfolio.service.monitoring

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CollectionStreamServiceTest {
  @Test
  fun `cannot send stale collections while monitoring refresh is failing`() {
    val metrics = mockk<CollectionMetricsService>(relaxed = true)
    every { metrics.ready() } returns false
    val stream = CollectionStreamService(metrics)
    stream.subscribe()
    stream.broadcast()
    verify(timeout = 5000, atLeast = 2) { metrics.ready() }
    verify(exactly = 0) { metrics.collections() }
  }
}
