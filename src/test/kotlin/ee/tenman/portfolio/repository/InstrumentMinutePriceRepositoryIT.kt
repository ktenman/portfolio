package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.InstrumentMinutePrice
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.job.CurrentDaySummaryRefreshJob
import ee.tenman.portfolio.job.TransactionRunner
import ee.tenman.portfolio.service.pricing.InstrumentMinutePriceService
import ee.tenman.portfolio.service.summary.CurrentDaySummaryCacheService
import ee.tenman.portfolio.service.summary.IntradaySummaryService
import ee.tenman.portfolio.service.summary.PlatformSummaryCacheService
import ee.tenman.portfolio.service.transaction.TransactionService
import io.mockk.every
import io.mockk.mockk
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@IntegrationTest
class InstrumentMinutePriceRepositoryIT {
  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var instrumentMinutePriceRepository: InstrumentMinutePriceRepository

  @Resource
  private lateinit var transactionRunner: TransactionRunner

  @Resource
  private lateinit var instrumentMinutePriceService: InstrumentMinutePriceService

  @Test
  fun `should capture only positive instrument prices`() {
    val positive = saveInstrument("POSITIVE", "12.345")
    saveInstrument("ZERO", "0")
    saveInstrument("NULL", null)

    capture("2026-09-21T10:15:42Z")

    val rows = instrumentMinutePriceRepository.findAll()
    expect(rows).toHaveSize(1)
    expect(rows.single().instrument.id).toEqual(positive.id)
    expect(rows.single().capturedAt).toEqual(Instant.parse("2026-09-21T10:15:00Z"))
    expect(rows.single().price).toEqualNumerically(BigDecimal("12.345"))
  }

  @Test
  fun `should keep the first price captured within a minute`() {
    val instrument = saveInstrument("SAME_MINUTE", "10")
    capture("2026-09-21T10:15:05Z")
    updatePrice(instrument.id, "11")

    capture("2026-09-21T10:15:55Z")

    val rows = instrumentMinutePriceRepository.findAll()
    expect(rows).toHaveSize(1)
    expect(rows.single().price).toEqualNumerically(BigDecimal("10"))
  }

  @Test
  fun `should capture a changed price in the next minute`() {
    val instrument = saveInstrument("CHANGED", "10")
    capture("2026-09-21T10:15:05Z")
    updatePrice(instrument.id, "11")

    capture("2026-09-21T10:16:05Z")

    val prices = instrumentMinutePriceRepository.findAll().sortedBy { it.capturedAt }.map { it.price.toInt() }
    expect(prices).toEqual(listOf(10, 11))
  }

  @Test
  fun `should capture an unchanged price after one full day`() {
    saveInstrument("HEARTBEAT", "10")
    capture("2026-09-20T10:15:00Z")
    capture("2026-09-21T10:15:00Z")
    capture("2026-09-21T10:16:00Z")

    val rows = instrumentMinutePriceRepository.findAll().sortedBy { it.capturedAt }
    expect(rows.map { it.capturedAt }).toEqual(
      listOf(
        Instant.parse("2026-09-20T10:15:00Z"),
        Instant.parse("2026-09-21T10:16:00Z"),
      ),
    )
  }

  @Test
  fun `should delete only prices older than the cutoff`() {
    val instrument = saveInstrument("CLEANUP", "10")
    savePrice(instrument, "2026-09-01T10:00:00Z", "9")
    savePrice(instrument, "2026-09-19T10:00:00Z", "10")

    transactionRunner.runInTransaction {
      InstrumentMinutePriceService(
        instrumentMinutePriceRepository,
        Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC),
      ).deleteOlderThan(Instant.parse("2026-09-10T00:00:00Z"))
    }

    val rows = instrumentMinutePriceRepository.findAll()
    expect(rows).toHaveSize(1)
    expect(rows.single().capturedAt).toEqual(Instant.parse("2026-09-19T10:00:00Z"))
  }

  @Test
  fun `should select carry forward and bounded replay rows in capture order`() {
    val first = saveInstrument("FIRST", "10")
    val second = saveInstrument("SECOND", "20")
    val excluded = saveInstrument("EXCLUDED", "30")
    savePrice(first, "2026-09-21T09:00:00Z", "9")
    savePrice(first, "2026-09-21T09:30:00Z", "10")
    savePrice(first, "2026-09-21T10:30:00Z", "11")
    savePrice(first, "2026-09-21T11:01:00Z", "12")
    savePrice(second, "2026-09-21T09:45:00Z", "20")
    savePrice(second, "2026-09-21T11:00:00Z", "21")
    savePrice(excluded, "2026-09-21T10:15:00Z", "30")

    val rows =
      instrumentMinutePriceRepository.findForReplay(
        instrumentIds = listOf(first.id, second.id),
        from = Instant.parse("2026-09-21T10:00:00Z"),
        until = Instant.parse("2026-09-21T11:00:00Z"),
      )

    expect(rows.map { it.capturedAt }).toEqual(
      listOf(
        Instant.parse("2026-09-21T09:30:00Z"),
        Instant.parse("2026-09-21T09:45:00Z"),
        Instant.parse("2026-09-21T10:30:00Z"),
        Instant.parse("2026-09-21T11:00:00Z"),
      ),
    )
    expect(rows.map { it.price.toInt() }).toEqual(listOf(10, 20, 11, 21))
  }

  @Test
  fun `should commit captured prices when summary refresh fails`() {
    val instrument = saveInstrument("SUMMARY_FAILURE", "42")
    val currentDaySummaryCacheService = mockk<CurrentDaySummaryCacheService>()
    every {
      currentDaySummaryCacheService.refreshCurrentDaySummary()
    } throws RuntimeException("summary unavailable")
    val job =
      CurrentDaySummaryRefreshJob(
        currentDaySummaryCacheService,
        mockk<PlatformSummaryCacheService>(relaxed = true),
        mockk<IntradaySummaryService>(relaxed = true),
        mockk<TransactionService>(relaxed = true),
        instrumentMinutePriceService,
      )

    job.refresh()

    val rows = instrumentMinutePriceRepository.findAll()
    expect(rows).toHaveSize(1)
    expect(rows.single().instrument.id).toEqual(instrument.id)
    expect(rows.single().price).toEqualNumerically(BigDecimal("42"))
  }

  private fun capture(at: String) {
    val clock = Clock.fixed(Instant.parse(at), ZoneOffset.UTC)
    transactionRunner.runInTransaction {
      InstrumentMinutePriceService(instrumentMinutePriceRepository, clock).record()
    }
  }

  private fun saveInstrument(
    symbol: String,
    price: String?,
    providerName: ProviderName = ProviderName.FT,
  ): Instrument =
    instrumentRepository.save(
      Instrument(
        symbol = symbol,
        name = symbol,
        category = "ETF",
        baseCurrency = "EUR",
        currentPrice = price?.let(::BigDecimal),
        providerName = providerName,
      ),
    )

  private fun updatePrice(
    instrumentId: Long,
    price: String,
  ) {
    transactionRunner.runInTransaction {
      instrumentRepository.updateCurrentPrice(instrumentId, BigDecimal(price))
    }
  }

  private fun savePrice(
    instrument: Instrument,
    capturedAt: String,
    price: String,
  ): InstrumentMinutePrice =
    instrumentMinutePriceRepository.save(
      InstrumentMinutePrice(
        instrument = instrument,
        capturedAt = Instant.parse(capturedAt),
        price = BigDecimal(price),
      ),
    )
}
