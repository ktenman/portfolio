package ee.tenman.portfolio.service.monitoring

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.repository.CollectionItemRepository
import ee.tenman.portfolio.repository.CollectionOperationRepository
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@IntegrationTest
class CollectionMonitorIT {
  @Resource private lateinit var monitor: CollectionMonitorService

  @Resource private lateinit var stateService: CollectionStateService

  @Resource private lateinit var operationRepository: CollectionOperationRepository

  @Resource private lateinit var itemRepository: CollectionItemRepository

  @Resource private lateinit var clock: Clock

  @Resource private lateinit var transactionManager: PlatformTransactionManager

  @Test
  fun `should retain partial item success without advancing full success`() {
    monitor.collect(CollectionKey.BINANCE_PRICES, listOf("A", "B")) { it.persisted("A") }
    val snapshot = stateService.snapshots().single { it.key == CollectionKey.BINANCE_PRICES }
    expect(snapshot.persisted).toEqual(1)
    expect(snapshot.failed).toEqual(1)
    expect(snapshot.lastFullSuccess).toEqual(null)
    expect(snapshot.itemSuccesses["A"]).notToEqualNull()
    expect(snapshot.itemSuccesses["B"]).toEqual(null)
  }

  @Test
  fun `should store the failure reason of each unpersisted item`() {
    monitor.collect(CollectionKey.BINANCE_PRICES, listOf("A", "B")) {
      it.persisted("A")
      it.failed("B", IllegalStateException("Ölihind puudub"))
    }
    val snapshot = stateService.snapshots().single { it.key == CollectionKey.BINANCE_PRICES }
    expect(snapshot.itemErrors).toEqual(mapOf("A" to null, "B" to "Ölihind puudub"))
  }

  @Test
  fun `should keep last full success through a later partial run`() {
    monitor.collect(CollectionKey.BINANCE_PRICES, listOf("A")) { it.persisted("A") }
    val previous = stateService.snapshots().single { it.key == CollectionKey.BINANCE_PRICES }.lastFullSuccess
    monitor.collect(CollectionKey.BINANCE_PRICES, listOf("A")) { }
    val current = stateService.snapshots().single { it.key == CollectionKey.BINANCE_PRICES }
    expect(previous).notToEqualNull()
    expect(current.lastFullSuccess).toEqual(previous)
  }

  @Test
  fun `should preserve first seen time and item success across service reconstruction`() {
    monitor.collect(CollectionKey.FT_HISTORY, listOf("A", "B")) { it.persisted("A") }
    val before = stateService.snapshots().single { it.key == CollectionKey.FT_HISTORY }
    val reconstructed = CollectionStateService(operationRepository, itemRepository, clock)
    val after = TransactionTemplate(transactionManager).execute { reconstructed.initialize(CollectionKey.FT_HISTORY, listOf("A")) }
    expect(after.initializedAt).toEqual(before.initializedAt)
    expect(after.expected).toEqual(setOf("A"))
    expect(after.itemSuccesses["A"]).toEqual(before.itemSuccesses["A"])
  }

  @Test
  fun `should count consecutive empty runs durably`() {
    repeat(2) { monitor.collect(CollectionKey.LIGHTYEAR_PRICES, listOf("A")) { } }
    val snapshot = stateService.snapshots().single { it.key == CollectionKey.LIGHTYEAR_PRICES }
    expect(snapshot.consecutiveEmptyRuns).toEqual(2)
  }

  @Test
  fun `should count fetched but unpersisted runs as empty`() {
    monitor.collect(CollectionKey.LIGHTYEAR_HOLDINGS, listOf("A")) { it.fetched("A") }
    val snapshot = stateService.snapshots().single { it.key == CollectionKey.LIGHTYEAR_HOLDINGS }
    expect(snapshot.consecutiveEmptyRuns).toEqual(1)
  }

  @Test
  fun `should expose committed item success before the run completes`() {
    assertThrows<IllegalStateException> {
      monitor.collect(CollectionKey.TRADING212_HOLDINGS, listOf("A", "B")) { run ->
        run.persisted("A")
        val during = stateService.snapshots().single { it.key == CollectionKey.TRADING212_HOLDINGS }
        expect(during.itemSuccesses["A"]).notToEqualNull()
        throw IllegalStateException("later item failed")
      }
    }
    val after = stateService.snapshots().single { it.key == CollectionKey.TRADING212_HOLDINGS }
    expect(after.itemSuccesses["A"]).notToEqualNull()
  }

  @Test
  fun `should suspend caller transaction around collection work`() {
    val active =
      TransactionTemplate(transactionManager).execute {
      monitor.collect(CollectionKey.BLACKROCK_HOLDINGS, listOf("A")) { TransactionSynchronizationManager.isActualTransactionActive() }
    }
    expect(active).toEqual(false)
  }

  @Test
  fun `should retain successes from concurrent runs for the same operation`() {
    Executors.newFixedThreadPool(2).use { executor ->
      val first = executor.submit { monitor.collect(CollectionKey.VANGUARD_HOLDINGS, listOf("A", "B")) { it.persisted("A") } }
      val second = executor.submit { monitor.collect(CollectionKey.VANGUARD_HOLDINGS, listOf("A", "B")) { it.persisted("B") } }
      first.get(10, TimeUnit.SECONDS)
      second.get(10, TimeUnit.SECONDS)
    }
    val snapshot = stateService.snapshots().single { it.key == CollectionKey.VANGUARD_HOLDINGS }
    expect(snapshot.itemSuccesses["A"]).notToEqualNull()
    expect(snapshot.itemSuccesses["B"]).notToEqualNull()
    expect(snapshot.lastFullSuccess).toEqual(null)
  }

  @Test
  fun `should reject persistence marking in rolled back transaction`() {
    assertThrows<IllegalStateException> {
      monitor.collect(CollectionKey.TRADING212_PRICES, listOf("A")) { run ->
        TransactionTemplate(transactionManager).execute {
          run.persisted("A")
          it.setRollbackOnly()
        }
      }
    }
    val snapshot = stateService.snapshots().single { it.key == CollectionKey.TRADING212_PRICES }
    expect(snapshot.itemSuccesses["A"]).toEqual(null)
    expect(snapshot.failed).toEqual(1)
  }
}
