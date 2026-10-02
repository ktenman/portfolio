package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import com.ninjasquad.springmockk.MockkBean
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.testing.fixture.answerPairs
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@IntegrationTest
class HoldingWriteLockIT {
  @Resource
  private lateinit var etfHoldingService: EtfHoldingService

  @Resource
  private lateinit var holdingReconciliationService: HoldingReconciliationService

  @MockkBean(relaxed = true)
  private lateinit var holdingIdentityService: HoldingIdentityService

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var etfHoldingRepository: EtfHoldingRepository

  @Resource
  private lateinit var etfPositionRepository: EtfPositionRepository

  private val date = LocalDate.of(2026, 9, 30)

  @BeforeEach
  fun setUp() {
    holdingIdentityService.answerPairs { null }
    etfPositionRepository.deleteAll()
    etfHoldingRepository.deleteAll()
    instrumentRepository.deleteAll()
    listOf("IITU", "VWCE").forEach {
      instrumentRepository.save(Instrument(symbol = it, name = it, category = "ETF", baseCurrency = "EUR"))
    }
  }

  @Test
  fun `should save two concurrent imports that create the same new holdings`() {
    val feed = (1..200).map { row("Ühisettevõte $it", "0.5", it) }

    concurrently({ etfHoldingService.saveHoldings("IITU", date, feed) }, { etfHoldingService.saveHoldings("VWCE", date, feed) })

    expect(etfPositionRepository.findAll()).toHaveSize(400)
  }

  @Test
  fun `should keep the snapshot weight when an import races a merge of its holding`() {
    etfHoldingRepository.save(EtfHolding(name = "NVIDIA"))
    etfHoldingRepository.save(EtfHolding(name = "NVIDIA CORP", ticker = "NVDA"))
    holdingIdentityService.answerPairs { it.existingName == "NVIDIA" && it.candidateName == "NVIDIA CORP" }
    val feed = listOf(row("NVIDIA CORP", "50.0", 1, "NVDA")) + (2..201).map { row("Ühisettevõte $it", "0.25", it) }

    concurrently({ etfHoldingService.saveHoldings("IITU", date, feed) }, { holdingReconciliationService.reconcile(dryRun = false) })

    expect(etfPositionRepository.findAll().sumOf { it.weightPercentage }).toEqualNumerically(BigDecimal("100.0"))
  }

  @Test
  fun `should keep the snapshot weight through repeated imports and reconciliations`() {
    holdingIdentityService.answerPairs { true }
    val feed = listOf(row("Alphabet", "4.0", 1), row("Alphabet Class A", "6.0", 2, "GOOGL"))
    etfHoldingService.saveHoldings("IITU", date, feed)
    holdingReconciliationService.reconcile(dryRun = false)
    etfHoldingService.saveHoldings("IITU", date, feed)

    val second = holdingReconciliationService.reconcile(dryRun = false)

    val weight = etfPositionRepository.findAll().sumOf { it.weightPercentage }
    expect(Triple(weight.compareTo(BigDecimal("10.0")), second.mergedGroups, etfHoldingRepository.count())).toEqual(Triple(0, 0, 1L))
  }

  private fun concurrently(vararg actions: () -> Unit) {
    val start = CountDownLatch(1)
    Executors.newVirtualThreadPerTaskExecutor().use { executor ->
      val tasks =
        actions.map { action ->
          executor.submit<Unit> {
            check(start.await(10, TimeUnit.SECONDS))
            action()
          }
        }
      start.countDown()
      tasks.forEach { it.get(60, TimeUnit.SECONDS) }
    }
  }

  private fun row(
    name: String,
    weight: String,
    rank: Int,
    ticker: String? = null,
  ) = HoldingData(name = name, ticker = ticker, sector = null, weight = BigDecimal(weight), rank = rank)
}
