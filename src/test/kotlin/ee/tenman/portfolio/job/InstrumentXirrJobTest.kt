package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class InstrumentXirrJobTest {
  private val instrumentService: InstrumentService = mockk(relaxed = true)
  private val dailyPriceService: DailyPriceService = mockk()
  private val xirrCalculationService: XirrCalculationService = XirrCalculationService()
  private val jobExecutionService: JobExecutionService = mockk(relaxed = true)
  private val fixedInstant: Instant = Instant.parse("2025-06-15T10:00:00Z")
  private val clock: Clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

  private val job =
    InstrumentXirrJob(
    instrumentService = instrumentService,
    dailyPriceService = dailyPriceService,
    xirrCalculationService = xirrCalculationService,
    jobExecutionService = jobExecutionService,
    clock = clock,
  )

  @Test
  fun `should calculate xirr using synthetic monthly investments`() {
    val instrument = createInstrument("AAPL", 1L, BigDecimal("150"))
    val dailyPrices = createMonthlyPrices(instrument, "2025-01-01", "2025-06-01")
    every { instrumentService.getAllInstrumentsWithoutFiltering() } returns listOf(instrument)
    every { dailyPriceService.findAllByInstrument(instrument) } returns dailyPrices
    val xirrSlot = slot<BigDecimal>()
    every { instrumentService.updateXirrAnnualReturn(1L, capture(xirrSlot)) } just Runs

    job.execute()

    verify(exactly = 1) { instrumentService.updateXirrAnnualReturn(1L, any()) }
    val capturedXirr = xirrSlot.captured
    expect(capturedXirr).toBeGreaterThan(BigDecimal("-10"))
    expect(capturedXirr).toBeLessThan(BigDecimal("10"))
  }

  @Test
  fun `should skip instruments with no price history`() {
    val instrument = createInstrument("AAPL", 1L, null)
    every { instrumentService.getAllInstrumentsWithoutFiltering() } returns listOf(instrument)
    every { dailyPriceService.findAllByInstrument(instrument) } returns emptyList()

    job.execute()

    verify(exactly = 0) { instrumentService.updateXirrAnnualReturn(any(), any()) }
  }

  @Test
  fun `should skip when no instruments exist`() {
    every { instrumentService.getAllInstrumentsWithoutFiltering() } returns emptyList()

    job.execute()

    verify(exactly = 0) { dailyPriceService.findAllByInstrument(any()) }
  }

  @Test
  fun `should set null when price data starts after calculation date`() {
    val instrument = createInstrument("AAPL", 1L, BigDecimal("100"))
    val futurePrice = createDailyPrice(instrument, "2025-07-01", BigDecimal("100"))
    every { instrumentService.getAllInstrumentsWithoutFiltering() } returns listOf(instrument)
    every { dailyPriceService.findAllByInstrument(instrument) } returns listOf(futurePrice)

    job.execute()

    verify(exactly = 1) { instrumentService.updateXirrAnnualReturn(1L, null) }
  }

  @Test
  fun `should handle errors gracefully and continue processing`() {
    val instrument1 = createInstrument("AAPL", 1L, BigDecimal("150"))
    val instrument2 = createInstrument("GOOGL", 2L, BigDecimal("180"))
    val prices2 = createMonthlyPrices(instrument2, "2025-01-01", "2025-06-01")
    every { instrumentService.getAllInstrumentsWithoutFiltering() } returns listOf(instrument1, instrument2)
    every { dailyPriceService.findAllByInstrument(instrument1) } throws RuntimeException("API error")
    every { dailyPriceService.findAllByInstrument(instrument2) } returns prices2

    job.execute()

    verify(exactly = 0) { instrumentService.updateXirrAnnualReturn(1L, any()) }
    verify(exactly = 1) { instrumentService.updateXirrAnnualReturn(2L, any()) }
  }

  @Test
  fun `should have correct job name`() {
    expect(job.getName()).toEqual("InstrumentXirrJob")
  }

  @Test
  fun `should calculate positive xirr for appreciating asset`() {
    val instrument = createInstrument("GROWTH", 1L, BigDecimal("200"))
    val prices =
      listOf(
      createDailyPrice(instrument, "2025-01-01", BigDecimal("100")),
      createDailyPrice(instrument, "2025-02-01", BigDecimal("110")),
      createDailyPrice(instrument, "2025-03-01", BigDecimal("120")),
      createDailyPrice(instrument, "2025-04-01", BigDecimal("140")),
      createDailyPrice(instrument, "2025-05-01", BigDecimal("160")),
      createDailyPrice(instrument, "2025-06-01", BigDecimal("180")),
    )
    every { instrumentService.getAllInstrumentsWithoutFiltering() } returns listOf(instrument)
    every { dailyPriceService.findAllByInstrument(instrument) } returns prices
    val xirrSlot = slot<BigDecimal>()
    every { instrumentService.updateXirrAnnualReturn(1L, capture(xirrSlot)) } just Runs

    job.execute()

    val capturedXirr = xirrSlot.captured
    expect(capturedXirr).toBeGreaterThan(BigDecimal.ZERO)
  }

  private fun createInstrument(
    symbol: String,
    id: Long,
    currentPrice: BigDecimal?,
  ): Instrument =
    mockk {
    every { this@mockk.id } returns id
    every { this@mockk.symbol } returns symbol
    every { this@mockk.currentPrice } returns currentPrice
  }

  private fun createDailyPrice(
    instrument: Instrument,
    date: String,
    price: BigDecimal,
  ): DailyPrice =
    mockk {
    every { this@mockk.instrument } returns instrument
    every { this@mockk.entryDate } returns LocalDate.parse(date)
    every { this@mockk.closePrice } returns price
    every { this@mockk.providerName } returns ProviderName.FT
  }

  private fun createMonthlyPrices(
    instrument: Instrument,
    startDate: String,
    endDate: String,
  ): List<DailyPrice> {
    val prices = mutableListOf<DailyPrice>()
    var current = LocalDate.parse(startDate)
    val end = LocalDate.parse(endDate)
    var price = BigDecimal("100")
    while (!current.isAfter(end)) {
      prices.add(createDailyPrice(instrument, current.toString(), price))
      current = current.plusMonths(1)
      price = price.add(BigDecimal("5"))
    }
    return prices
  }
}
