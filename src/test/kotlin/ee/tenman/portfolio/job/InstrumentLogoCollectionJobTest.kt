package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.logo.LogoFallbackService
import ee.tenman.portfolio.service.logo.LogoFetchResult
import ee.tenman.portfolio.service.logo.NvstlyLogoService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test

class InstrumentLogoCollectionJobTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val instrumentService = mockk<InstrumentService>(relaxed = true)
  private val lightyearPriceService = mockk<LightyearPriceService>()
  private val nvstlyLogoService = mockk<NvstlyLogoService>()
  private val logoFallbackService = mockk<LogoFallbackService>()
  private val imageProcessingService = mockk<ImageProcessingService>()
  private val minioService = mockk<MinioService>(relaxed = true)
  private val job =
    InstrumentLogoCollectionJob(
      instrumentRepository,
      instrumentService,
      lightyearPriceService,
      nvstlyLogoService,
      logoFallbackService,
      imageProcessingService,
      minioService,
    )

  @Test
  fun `uploads the Lightyear logo of an ETF and records its source`() {
    val etf = etf()
    given(etf)
    every { lightyearPriceService.fetchLogoUrls(listOf("VWCE:GER:EUR")) } returns mapOf("VWCE:GER:EUR" to LOGO_URL)
    every { logoFallbackService.fetchLogo("Vanguard", null, LOGO_URL) } returns
      LogoFetchResult(imageData = IMAGE, source = LogoSource.LIGHTYEAR)

    job.collectMissingLogos()

    verifyOrder {
      minioService.uploadLogo(etf.uuid, RESIZED)
      instrumentService.updateLogoSource(1L, "VWCE:GER:EUR", LogoSource.LIGHTYEAR)
    }
  }

  @Test
  fun `uploads the crypto icon of a Binance instrument without searching further`() {
    given(bitcoin())
    every { nvstlyLogoService.fetchCryptoLogo("BTC") } returns IMAGE

    job.collectMissingLogos()

    verify(exactly = 1) { instrumentService.updateLogoSource(2L, "BTCEUR", LogoSource.NVSTLY_ICONS) }
    verify(exactly = 0) { logoFallbackService.fetchLogo(any(), any(), any()) }
  }

  @Test
  fun `searches for a Binance instrument whose crypto icon is missing`() {
    given(bitcoin())
    every { nvstlyLogoService.fetchCryptoLogo("BTC") } returns null
    every { logoFallbackService.fetchLogo("BTC/EUR", null, null) } returns
      LogoFetchResult(imageData = IMAGE, source = LogoSource.LLM_SELECTED)

    job.collectMissingLogos()

    verify(exactly = 1) { instrumentService.updateLogoSource(2L, "BTCEUR", LogoSource.LLM_SELECTED) }
  }

  @Test
  fun `searches the logo of the issuer without a ticker when Lightyear has no logo for a fund`() {
    given(avivaFund())
    every { lightyearPriceService.fetchLogoUrls(listOf("GB00B0ZDNB53:GBP")) } returns emptyMap()
    every { logoFallbackService.fetchLogo("Aviva", null, null) } returns null

    job.collectMissingLogos()

    verify(exactly = 1) { logoFallbackService.fetchLogo("Aviva", null, null) }
  }

  @Test
  fun `searches the logo of a stock by its full name`() {
    given(instrument(3L, "ÖSTPOST:VIE:EUR", "Österreichische Post", "STOCK", ProviderName.FT))
    every { logoFallbackService.fetchLogo("Österreichische Post", null, null) } returns null

    job.collectMissingLogos()

    verify(exactly = 1) { logoFallbackService.fetchLogo("Österreichische Post", null, null) }
  }

  @Test
  fun `asks Lightyear only for ETF logos`() {
    given(etf(), bitcoin(), instrument(3L, "ÖSTPOST:VIE:EUR", "Österreichische Post", "STOCK", ProviderName.FT))
    every { nvstlyLogoService.fetchCryptoLogo("BTC") } returns null
    every { logoFallbackService.fetchLogo(any(), null, null) } returns null

    job.collectMissingLogos()

    verify(exactly = 1) { lightyearPriceService.fetchLogoUrls(listOf("VWCE:GER:EUR")) }
  }

  @Test
  fun `asks Lightyear for the logos of ETFs from every provider`() {
    given(etf(), trading212Etf())
    every { lightyearPriceService.fetchLogoUrls(listOf("VWCE:GER:EUR", "BNKE:PAR:EUR")) } returns emptyMap()
    every { logoFallbackService.fetchLogo(any(), null, null) } returns null

    job.collectMissingLogos()

    verify(exactly = 1) { lightyearPriceService.fetchLogoUrls(listOf("VWCE:GER:EUR", "BNKE:PAR:EUR")) }
  }

  @Test
  fun `skips cash instruments`() {
    given(instrument(4L, "EUR", "Euro", "CASH", ProviderName.MANUAL))

    job.collectMissingLogos()

    verify(exactly = 0) { logoFallbackService.fetchLogo(any(), any(), any()) }
  }

  @Test
  fun `leaves the logo source empty when the upload fails`() {
    given(bitcoin())
    every { nvstlyLogoService.fetchCryptoLogo("BTC") } returns IMAGE
    every { minioService.uploadLogo(any(), any()) } throws RuntimeException("MinIO unavailable")

    job.collectMissingLogos()

    verify(exactly = 0) { instrumentService.updateLogoSource(any(), any(), any()) }
  }

  @Test
  fun `keeps collecting the other instruments after one fails`() {
    given(etf(), bitcoin())
    every { lightyearPriceService.fetchLogoUrls(listOf("VWCE:GER:EUR")) } returns emptyMap()
    every { logoFallbackService.fetchLogo("Vanguard", null, null) } throws RuntimeException("Search failed")
    every { nvstlyLogoService.fetchCryptoLogo("BTC") } returns IMAGE

    job.collectMissingLogos()

    verify(exactly = 1) { instrumentService.updateLogoSource(2L, "BTCEUR", LogoSource.NVSTLY_ICONS) }
  }

  @Test
  fun `keeps collecting when the Lightyear lookup fails`() {
    given(etf(), bitcoin())
    every { lightyearPriceService.fetchLogoUrls(any()) } throws RuntimeException("Proxy unavailable")
    every { nvstlyLogoService.fetchCryptoLogo("BTC") } returns IMAGE

    job.collectMissingLogos()

    verify(exactly = 1) { instrumentService.updateLogoSource(2L, "BTCEUR", LogoSource.NVSTLY_ICONS) }
  }

  @Test
  fun `leaves ETFs for the next run when the Lightyear batch fails`() {
    given(etf())
    every { lightyearPriceService.fetchLogoUrls(listOf("VWCE:GER:EUR")) } returns null

    job.collectMissingLogos()

    verify(exactly = 0) { logoFallbackService.fetchLogo(any(), any(), any()) }
  }

  @Test
  fun `leaves a Trading212 ETF for the next run when the Lightyear batch fails`() {
    given(etf(), trading212Etf())
    every { lightyearPriceService.fetchLogoUrls(listOf("VWCE:GER:EUR", "BNKE:PAR:EUR")) } returns null

    job.collectMissingLogos()

    verify(exactly = 0) { logoFallbackService.fetchLogo(any(), any(), any()) }
  }

  @Test
  fun `leaves ETFs for the next run when the Lightyear lookup throws`() {
    given(etf())
    every { lightyearPriceService.fetchLogoUrls(any()) } throws RuntimeException("Proxy unavailable")

    job.collectMissingLogos()

    verify(exactly = 0) { logoFallbackService.fetchLogo(any(), any(), any()) }
  }

  private fun given(vararg instruments: Instrument) {
    every { instrumentRepository.findByLogoSourceIsNull() } returns instruments.toList()
    every { lightyearPriceService.fetchLogoUrls(emptyList()) } returns emptyMap()
    every { imageProcessingService.resizeToMaxDimension(IMAGE) } returns RESIZED
  }

  private fun etf() = instrument(1L, "VWCE:GER:EUR", "Vanguard FTSE All-World", "ETF", ProviderName.LIGHTYEAR)

  private fun bitcoin() = instrument(2L, "BTCEUR", "BTC/EUR", "Cryptocurrency", ProviderName.BINANCE)

  private fun trading212Etf() = instrument(5L, "BNKE:PAR:EUR", "Amundi Euro Stoxx Banks", "ETF", ProviderName.TRADING212)

  private fun avivaFund() =
    instrument(6L, "GB00B0ZDNB53:GBP", "Aviva BlackRock Aquila US Equity Index Tracker 6 Pension Fund", "ETF", ProviderName.FT)

  private fun instrument(
    id: Long,
    symbol: String,
    name: String,
    category: String,
    providerName: ProviderName,
  ) = Instrument(symbol = symbol, name = name, category = category, baseCurrency = "EUR", providerName = providerName)
    .apply { this.id = id }

  companion object {
    private const val LOGO_URL = "https://lightyear.test/vwce.png"
    private val IMAGE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    private val RESIZED = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00)
  }
}
