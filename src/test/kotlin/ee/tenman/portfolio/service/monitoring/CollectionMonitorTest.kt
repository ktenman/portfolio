package ee.tenman.portfolio.service.monitoring

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.model.CollectionSnapshot
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant

class CollectionMonitorTest {
  @Test
  fun `should report durable initialization failure and preserve its exception`() {
    val state = mockk<CollectionStateService>()
    val error = DataAccessResourceFailureException("database unavailable")
    every { state.initialize(CollectionKey.BINANCE_PRICES, any()) } throws error
    val registry = SimpleMeterRegistry()
    val thrown =
      assertThrows<DataAccessResourceFailureException> {
      CollectionMonitorService(state, registry, mockk(relaxed = true)).collect(CollectionKey.BINANCE_PRICES, listOf("A")) { }
    }
    expect(thrown).toEqual(error)
    expect(persistenceFailures(registry)).toEqual(1.0)
  }

  @Test
  fun `should retain action failure when durable completion also fails`() {
    val state = mockk<CollectionStateService>()
    val actionError = IllegalStateException("collection failed")
    val storageError = DataAccessResourceFailureException("database unavailable")
    every { state.initialize(CollectionKey.BINANCE_PRICES, any()) } returns mockk<CollectionSnapshot>()
    every { state.begin(CollectionKey.BINANCE_PRICES) } returns Instant.EPOCH
    every { state.finish(CollectionKey.BINANCE_PRICES, any(), any(), any()) } throws storageError
    val registry = SimpleMeterRegistry()
    val thrown =
      assertThrows<IllegalStateException> {
      CollectionMonitorService(
        state,
        registry,
        mockk(relaxed = true),
      ).collect(CollectionKey.BINANCE_PRICES, listOf("A")) { throw actionError }
    }
    expect(thrown).toEqual(actionError)
    expect(thrown.suppressed.toList()).toEqual(listOf(storageError))
    expect(persistenceFailures(registry)).toEqual(1.0)
  }

  @Test
  fun `should publish the collection status when the run begins and when it finishes`() {
    val state = mockk<CollectionStateService>()
    val stream = mockk<CollectionStreamService>(relaxed = true)
    every { state.initialize(CollectionKey.BINANCE_PRICES, any()) } returns mockk<CollectionSnapshot>()
    every { state.begin(CollectionKey.BINANCE_PRICES) } returns Instant.EPOCH
    every { state.finish(CollectionKey.BINANCE_PRICES, any(), any(), any()) } just runs
    CollectionMonitorService(state, SimpleMeterRegistry(), stream).collect(CollectionKey.BINANCE_PRICES, listOf("Ärikinnisvara")) { }
    verifyOrder {
      state.begin(CollectionKey.BINANCE_PRICES)
      stream.publish()
      state.finish(CollectionKey.BINANCE_PRICES, any(), any(), any())
      stream.publish()
    }
  }

  private fun persistenceFailures(registry: SimpleMeterRegistry): Double =
    registry
      .get("portfolio.collection.failures")
      .tags("provider", "binance", "operation", "prices", "category", "persistence", "status", "none")
      .counter()
      .count()
}
