package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.model.InstrumentSnapshot
import ee.tenman.portfolio.repository.InstrumentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

class InstrumentServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val transactionProfitService = mockk<TransactionProfitService>(relaxed = true)
  private val instrumentSnapshotService = mockk<InstrumentSnapshotService>()
  private val cacheInvalidationService = mockk<CacheInvalidationService>(relaxed = true)

  private lateinit var instrumentService: InstrumentService
  private lateinit var testInstrument: Instrument

  @BeforeEach
  fun setUp() {
    testInstrument =
      Instrument(
        symbol = "AAPL",
        name = "Apple Inc.",
        category = "Stock",
        baseCurrency = "USD",
        currentPrice = BigDecimal("150.00"),
        providerName = ProviderName.FT,
      ).apply { id = 1L }

    instrumentService =
      InstrumentService(
        instrumentRepository,
        transactionProfitService,
        instrumentSnapshotService,
        cacheInvalidationService,
      )
  }

  @Test
  fun `should return instrument when found by id`() {
    every { instrumentRepository.findById(1L) } returns Optional.of(testInstrument)

    val result = instrumentService.getInstrumentById(1L)

    expect(result).toEqual(testInstrument)
    expect(result.symbol).toEqual("AAPL")
    verify { instrumentRepository.findById(1L) }
  }

  @Test
  fun `should throw exception when instrument not found by id`() {
    every { instrumentRepository.findById(999L) } returns Optional.empty()

    expect {
      instrumentService.getInstrumentById(999L)
    }.toThrow<RuntimeException> {
      messageToContain("Instrument not found with id: 999")
    }
  }

  @Test
  fun `should return instrument when found by symbol`() {
    every { instrumentRepository.findBySymbol("AAPL") } returns Optional.of(testInstrument)

    val result = instrumentService.findBySymbol("AAPL")

    expect(result).toEqual(testInstrument)
    verify { instrumentRepository.findBySymbol("AAPL") }
  }

  @Test
  fun `should throw exception when instrument not found by symbol`() {
    every { instrumentRepository.findBySymbol("UNKNOWN") } returns Optional.empty()

    expect {
      instrumentService.findBySymbol("UNKNOWN")
    }.toThrow<RuntimeException> {
      messageToContain("Instrument not found with symbol: UNKNOWN")
    }
  }

  @Test
  fun `should save instrument and recalculate profits`() {
    every { instrumentRepository.save(testInstrument) } returns testInstrument

    val result = instrumentService.saveInstrument(testInstrument)

    expect(result).toEqual(testInstrument)
    verify { instrumentRepository.save(testInstrument) }
    verify { transactionProfitService.recalculateProfitsForInstrument(1L) }
    verify { cacheInvalidationService.evictAllRelatedCaches(1L, "AAPL") }
  }

  @Test
  fun `should delete instrument and evict cache`() {
    every { instrumentRepository.findById(1L) } returns Optional.of(testInstrument)
    every { instrumentRepository.deleteById(1L) } returns Unit

    instrumentService.deleteInstrument(1L)

    verify { instrumentRepository.deleteById(1L) }
    verify { cacheInvalidationService.evictInstrumentCaches(1L, "AAPL") }
  }

  @Test
  fun `should delete instrument when not found and evict cache with null symbol`() {
    every { instrumentRepository.findById(999L) } returns Optional.empty()
    every { instrumentRepository.deleteById(999L) } returns Unit

    instrumentService.deleteInstrument(999L)

    verify { instrumentRepository.deleteById(999L) }
    verify { cacheInvalidationService.evictInstrumentCaches(999L, null) }
  }

  @Test
  fun `should return all instruments without filtering`() {
    every { instrumentRepository.findAll() } returns listOf(testInstrument)

    val result = instrumentService.getAllInstrumentsWithoutFiltering()

    expect(result).toEqual(listOf(testInstrument))
    verify { instrumentRepository.findAll() }
  }

  @Test
  fun `should delegate to snapshot service for getAllInstrumentSnapshots`() {
    val snapshot = InstrumentSnapshot(testInstrument)
    every { instrumentSnapshotService.getAllSnapshots() } returns listOf(snapshot)

    val result = instrumentService.getAllInstrumentSnapshots()

    expect(result).toEqual(listOf(snapshot))
    verify { instrumentSnapshotService.getAllSnapshots() }
  }

  @Test
  fun `should delegate to snapshot service for getAllInstrumentSnapshots with platforms`() {
    val snapshot = InstrumentSnapshot(testInstrument)
    every { instrumentSnapshotService.getAllSnapshots(listOf("LHV")) } returns listOf(snapshot)

    val result = instrumentService.getAllInstrumentSnapshots(listOf("LHV"))

    expect(result).toEqual(listOf(snapshot))
    verify { instrumentSnapshotService.getAllSnapshots(listOf("LHV")) }
  }

  @Test
  fun `should delegate to snapshot service for getAllInstrumentSnapshots with platforms and period`() {
    val snapshot = InstrumentSnapshot(testInstrument)
    every { instrumentSnapshotService.getAllSnapshots(listOf("LHV"), "P7D") } returns listOf(snapshot)

    val result = instrumentService.getAllInstrumentSnapshots(listOf("LHV"), "P7D")

    expect(result).toEqual(listOf(snapshot))
    verify { instrumentSnapshotService.getAllSnapshots(listOf("LHV"), "P7D") }
  }

  @Test
  fun `should update current price using direct query and recalculate profits`() {
    val newPrice = BigDecimal("175.50")
    every { instrumentRepository.updateCurrentPrice(1L, newPrice) } returns Unit
    every { instrumentRepository.findById(1L) } returns Optional.of(testInstrument)

    instrumentService.updateCurrentPrice(1L, newPrice)

    verify { instrumentRepository.updateCurrentPrice(1L, newPrice) }
    verify { transactionProfitService.recalculateProfitsForInstrument(1L) }
    verify { cacheInvalidationService.evictAllRelatedCaches(1L, "AAPL") }
  }

  @Test
  fun `should update current price to null and still recalculate profits`() {
    every { instrumentRepository.updateCurrentPrice(1L, null) } returns Unit
    every { instrumentRepository.findById(1L) } returns Optional.of(testInstrument)

    instrumentService.updateCurrentPrice(1L, null)

    verify { instrumentRepository.updateCurrentPrice(1L, null) }
    verify { transactionProfitService.recalculateProfitsForInstrument(1L) }
    verify { cacheInvalidationService.evictAllRelatedCaches(1L, "AAPL") }
  }

  @Test
  fun `should not recalculate profits when instrument not found after price update`() {
    val newPrice = BigDecimal("175.50")
    every { instrumentRepository.updateCurrentPrice(999L, newPrice) } returns Unit
    every { instrumentRepository.findById(999L) } returns Optional.empty()

    instrumentService.updateCurrentPrice(999L, newPrice)

    verify { instrumentRepository.updateCurrentPrice(999L, newPrice) }
    verify(exactly = 0) { transactionProfitService.recalculateProfitsForInstrument(any()) }
    verify(exactly = 0) { cacheInvalidationService.evictAllRelatedCaches(any(), any()) }
  }
}
