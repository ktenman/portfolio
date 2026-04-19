package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.configuration.Trading212SymbolEntry
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.lightyear.LightyearFundInfoData
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.instrument.FundCurrencyResolverService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.trading212.Trading212HoldingsService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TerUpdateJobTest {
  private val jobTransactionService = mockk<JobTransactionService>()
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val lightyearPriceService = mockk<LightyearPriceService>()
  private val instrumentService = mockk<InstrumentService>()
  private val trading212HoldingsService = mockk<Trading212HoldingsService>()
  private val scrapingProperties = Trading212ScrapingProperties()
  private val fundCurrencyResolver = mockk<FundCurrencyResolverService>()
  private val clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"))
  private lateinit var job: TerUpdateJob

  @BeforeEach
  fun setUp() {
    every { jobTransactionService.saveJobExecution(any(), any(), any(), any(), any()) } returns mockk()
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns emptyList()
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns emptyList()
    every { fundCurrencyResolver.resolve(any(), any()) } returns null
    job =
      TerUpdateJob(
        jobTransactionService,
        instrumentRepository,
        lightyearPriceService,
        instrumentService,
        trading212HoldingsService,
        scrapingProperties,
        fundCurrencyResolver,
        clock,
      )
  }

  @Test
  fun `should update TER for all lightyear instruments`() {
    val instrument1 = createInstrument(1L, "VUAA", ProviderName.LIGHTYEAR)
    val instrument2 = createInstrument(2L, "VWCE", ProviderName.LIGHTYEAR)
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(instrument1, instrument2)
    every { lightyearPriceService.fetchFundInfo("VUAA") } returns LightyearFundInfoData(ter = BigDecimal("0.07"), fundCurrency = null)
    every { lightyearPriceService.fetchFundInfo("VWCE") } returns LightyearFundInfoData(ter = BigDecimal("0.22"), fundCurrency = null)
    every { instrumentService.updateTer(any(), any()) } just runs

    job.execute()

    verify { instrumentService.updateTer(1L, BigDecimal("0.07")) }
    verify { instrumentService.updateTer(2L, BigDecimal("0.22")) }
  }

  @Test
  fun `should skip instruments with no TER data`() {
    val instrument1 = createInstrument(1L, "VUAA", ProviderName.LIGHTYEAR)
    val instrument2 = createInstrument(2L, "BTCEUR", ProviderName.LIGHTYEAR)
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(instrument1, instrument2)
    every { lightyearPriceService.fetchFundInfo("VUAA") } returns LightyearFundInfoData(ter = BigDecimal("0.07"), fundCurrency = null)
    every { lightyearPriceService.fetchFundInfo("BTCEUR") } returns null
    every { instrumentService.updateTer(any(), any()) } just runs

    job.execute()

    verify { instrumentService.updateTer(1L, BigDecimal("0.07")) }
    verify(exactly = 0) { instrumentService.updateTer(2L, any()) }
  }

  @Test
  fun `should handle empty instrument list`() {
    job.execute()

    verify(exactly = 0) { lightyearPriceService.fetchFundInfo(any()) }
    verify(exactly = 0) { trading212HoldingsService.fetchTer(any()) }
    verify(exactly = 0) { instrumentService.updateTer(any(), any()) }
  }

  @Test
  fun `should save job execution with success status`() {
    val statusSlot = slot<JobStatus>()
    val messageSlot = slot<String>()
    every { jobTransactionService.saveJobExecution(any(), any(), any(), capture(statusSlot), capture(messageSlot)) } returns mockk()

    job.runJob()

    expect(statusSlot.captured).toEqual(JobStatus.SUCCESS)
    expect(messageSlot.captured).toContain("Lightyear: updated 0/0")
  }

  @Test
  fun `should save job execution with failure status on exception`() {
    val statusSlot = slot<JobStatus>()
    val messageSlot = slot<String>()
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } throws RuntimeException("Database error")
    every { jobTransactionService.saveJobExecution(any(), any(), any(), capture(statusSlot), capture(messageSlot)) } returns mockk()

    job.runJob()

    expect(statusSlot.captured).toEqual(JobStatus.FAILURE)
    expect(messageSlot.captured).toContain("Database error")
  }

  @Test
  fun `should update TER for TRADING212 instrument using Trading212HoldingsService`() {
    val bnke = createInstrument(42L, "BNKE:PAR:EUR", ProviderName.TRADING212)
    scrapingProperties.symbols.add(Trading212SymbolEntry(symbol = "BNKE:PAR:EUR", ticker = "BNKEp_EQ"))
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns listOf(bnke)
    every { trading212HoldingsService.fetchTer("BNKEp_EQ") } returns BigDecimal("0.3")
    every { instrumentService.updateTer(42L, BigDecimal("0.3")) } just runs

    job.execute()

    verify { instrumentService.updateTer(42L, BigDecimal("0.3")) }
  }

  @Test
  fun `should skip TRADING212 instrument when no ticker mapping exists`() {
    val unknown = createInstrument(99L, "UNKNOWN:X:EUR", ProviderName.TRADING212)
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns listOf(unknown)

    job.execute()

    verify(exactly = 0) { trading212HoldingsService.fetchTer(any()) }
    verify(exactly = 0) { instrumentService.updateTer(any(), any()) }
  }

  @Test
  fun `should skip TRADING212 instrument when fetchTer returns null`() {
    val bnke = createInstrument(42L, "BNKE:PAR:EUR", ProviderName.TRADING212)
    scrapingProperties.symbols.add(Trading212SymbolEntry(symbol = "BNKE:PAR:EUR", ticker = "BNKEp_EQ"))
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns listOf(bnke)
    every { trading212HoldingsService.fetchTer("BNKEp_EQ") } returns null

    job.execute()

    verify(exactly = 0) { instrumentService.updateTer(any(), any()) }
  }

  @Test
  fun `should continue processing when TRADING212 fetchTer throws`() {
    val bnke = createInstrument(42L, "BNKE:PAR:EUR", ProviderName.TRADING212)
    val other = createInstrument(43L, "OTHER:X:EUR", ProviderName.TRADING212)
    scrapingProperties.symbols.add(Trading212SymbolEntry(symbol = "BNKE:PAR:EUR", ticker = "BNKEp_EQ"))
    scrapingProperties.symbols.add(Trading212SymbolEntry(symbol = "OTHER:X:EUR", ticker = "OTHERp_EQ"))
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns listOf(bnke, other)
    every { trading212HoldingsService.fetchTer("BNKEp_EQ") } throws RuntimeException("network")
    every { trading212HoldingsService.fetchTer("OTHERp_EQ") } returns BigDecimal("0.25")
    every { instrumentService.updateTer(43L, BigDecimal("0.25")) } just runs

    job.execute()

    verify(exactly = 0) { instrumentService.updateTer(42L, any()) }
    verify { instrumentService.updateTer(43L, BigDecimal("0.25")) }
  }

  @Test
  fun `should update TERs for both Lightyear and Trading212 instruments`() {
    val lyInstrument = createInstrument(1L, "VUAA", ProviderName.LIGHTYEAR)
    val bnke = createInstrument(42L, "BNKE:PAR:EUR", ProviderName.TRADING212)
    scrapingProperties.symbols.add(Trading212SymbolEntry(symbol = "BNKE:PAR:EUR", ticker = "BNKEp_EQ"))
    every { instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR) } returns listOf(lyInstrument)
    every { instrumentRepository.findByProviderName(ProviderName.TRADING212) } returns listOf(bnke)
    every { lightyearPriceService.fetchFundInfo("VUAA") } returns LightyearFundInfoData(ter = BigDecimal("0.07"), fundCurrency = null)
    every { trading212HoldingsService.fetchTer("BNKEp_EQ") } returns BigDecimal("0.3")
    every { instrumentService.updateTer(any(), any()) } just runs

    job.execute()

    verify { instrumentService.updateTer(1L, BigDecimal("0.07")) }
    verify { instrumentService.updateTer(42L, BigDecimal("0.3")) }
  }

  private fun createInstrument(
    id: Long,
    symbol: String,
    providerName: ProviderName = ProviderName.LIGHTYEAR,
  ): Instrument {
    val instrument =
      Instrument(
        symbol = symbol,
        name = "Test Instrument",
        category = "ETF",
        baseCurrency = "EUR",
        providerName = providerName,
      )
    instrument.id = id
    return instrument
  }
}
