package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkSpyBean
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.MinioProperties
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.service.logo.LogoFallbackService
import ee.tenman.portfolio.service.logo.LogoFetchResult
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.RemoveObjectArgs
import io.mockk.every
import io.mockk.verify
import jakarta.annotation.Resource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.wiremock.spring.InjectWireMock
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class EtfHoldingsServiceIT {
  @Resource
  private lateinit var etfHoldingsService: EtfHoldingsService

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var etfHoldingRepository: EtfHoldingRepository

  @Resource
  private lateinit var etfPositionRepository: EtfPositionRepository

  @MockkSpyBean
  private lateinit var minioService: MinioService

  @MockkBean
  private lateinit var logoFallbackService: LogoFallbackService

  @Resource
  private lateinit var minioClient: MinioClient

  @Resource
  private lateinit var minioProperties: MinioProperties

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  private lateinit var testEtf: Instrument

  private val testDate = LocalDate.of(2024, 1, 15)

  private val testLogoData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

  @BeforeEach
  fun setup() {
    etfPositionRepository.deleteAll()
    etfHoldingRepository.deleteAll()
    instrumentRepository.deleteAll()

    testEtf =
      instrumentRepository.save(
        Instrument(
          symbol = "IITU",
          name = "iShares Global Clean Energy ETF",
          category = "ETF",
          baseCurrency = "EUR",
        ),
      )

    wireMockServer.stubFor(
      get(urlEqualTo("/test-logo.png"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "image/png")
            .withBody(testLogoData),
        ),
    )

    every { logoFallbackService.fetchLogo(any(), any(), isNull()) } returns null
  }

  @AfterEach
  fun cleanup() {
    val objects =
      minioClient.listObjects(
        ListObjectsArgs
          .builder()
          .bucket(minioProperties.bucketName)
          .prefix("logos/")
          .build(),
      )

    objects.forEach { result ->
      val objectName = result.get().objectName()
      minioClient.removeObject(
        RemoveObjectArgs
          .builder()
          .bucket(minioProperties.bucketName)
          .`object`(objectName)
          .build(),
      )
    }
  }

  @Test
  fun `should upload logo only once for new holding`() {
    val logoUrl = "http://localhost:${wireMockServer.port()}/test-logo.png"
    every { logoFallbackService.fetchLogo("Apple Inc", "AAPL", logoUrl) } returns
      LogoFetchResult(imageData = testLogoData, source = LogoSource.LIGHTYEAR)
    val holdings =
      listOf(
        HoldingData(
          name = "Apple Inc",
          ticker = "AAPL",
          sector = "Technology",
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = logoUrl,
        ),
      )

    etfHoldingsService.saveHoldings("IITU", testDate, holdings)
    val savedHolding = etfHoldingRepository.findAll().first()

    verify(exactly = 1) { minioService.logoExists(savedHolding.id) }
    verify(exactly = 1) { minioService.uploadLogo(savedHolding.id, any()) }
  }

  @Test
  fun `should not upload logo if it already exists`() {
    val logoUrl = "http://localhost:${wireMockServer.port()}/test-logo.png"
    every { logoFallbackService.fetchLogo("Microsoft Corp", "MSFT", logoUrl) } returns
      LogoFetchResult(imageData = testLogoData, source = LogoSource.LIGHTYEAR)
    val holdings =
      listOf(
        HoldingData(
          name = "Microsoft Corp",
          ticker = "MSFT",
          sector = "Technology",
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = logoUrl,
        ),
      )

    etfHoldingsService.saveHoldings("IITU", testDate, holdings)
    val savedHolding = etfHoldingRepository.findAll().first()

    verify(atLeast = 1) { minioService.logoExists(savedHolding.id) }
    verify(atLeast = 1) { minioService.uploadLogo(savedHolding.id, any()) }

    val logoExistsBefore = minioService.logoExists(savedHolding.id)
    expect(logoExistsBefore).toEqual(true)

    etfHoldingsService.saveHoldings("IITU", testDate.plusDays(1), holdings)

    val logoExistsAfter = minioService.logoExists(savedHolding.id)
    expect(logoExistsAfter).toEqual(true)
  }

  @Test
  fun `should skip upload when logoUrl is null`() {
    val holdings =
      listOf(
        HoldingData(
          name = "Tesla Inc",
          ticker = "TSLA",
          sector = "Automotive",
          weight = BigDecimal("5.0"),
          rank = 1,
          logoUrl = null,
        ),
      )

    etfHoldingsService.saveHoldings("IITU", testDate, holdings)

    val savedHoldings = etfHoldingRepository.findAll()
    expect(savedHoldings.size).toEqual(1)
    expect(savedHoldings.first().ticker).toEqual("TSLA")

    val tslaExists = minioService.logoExists(savedHoldings.first().id)
    expect(tslaExists).toEqual(false)
  }

  @Test
  fun `should handle multiple holdings with mixed logo states`() {
    val logoUrl = "http://localhost:${wireMockServer.port()}/test-logo.png"
    every { logoFallbackService.fetchLogo("NVIDIA Corp", "NVDA", logoUrl) } returns
      LogoFetchResult(imageData = testLogoData, source = LogoSource.LIGHTYEAR)
    every { logoFallbackService.fetchLogo("Amazon.com Inc", "AMZN", logoUrl) } returns
      LogoFetchResult(imageData = testLogoData, source = LogoSource.LIGHTYEAR)
    every { logoFallbackService.fetchLogo("Meta Platforms Inc", "META", isNull()) } returns null

    val holdings =
      listOf(
        HoldingData(
          name = "NVIDIA Corp",
          ticker = "NVDA",
          sector = "Technology",
          weight = BigDecimal("15.0"),
          rank = 1,
          logoUrl = logoUrl,
        ),
        HoldingData(
          name = "Amazon.com Inc",
          ticker = "AMZN",
          sector = "Consumer Cyclical",
          weight = BigDecimal("12.0"),
          rank = 2,
          logoUrl = logoUrl,
        ),
        HoldingData(
          name = "Meta Platforms Inc",
          ticker = "META",
          sector = "Technology",
          weight = BigDecimal("8.0"),
          rank = 3,
          logoUrl = null,
        ),
      )

    etfHoldingsService.saveHoldings("IITU", testDate, holdings)

    val savedHoldings = etfHoldingRepository.findAll()
    expect(savedHoldings.size).toEqual(3)

    val nvda = savedHoldings.find { it.ticker == "NVDA" }!!
    val amzn = savedHoldings.find { it.ticker == "AMZN" }!!
    val meta = savedHoldings.find { it.ticker == "META" }!!

    val nvdaExists = minioService.logoExists(nvda.id)
    expect(nvdaExists).toEqual(true)

    val amznExists = minioService.logoExists(amzn.id)
    expect(amznExists).toEqual(true)

    val metaExists = minioService.logoExists(meta.id)
    expect(metaExists).toEqual(false)
  }

  @Test
  fun `should update sector from source when existing holding has no sector`() {
    val holdingsWithoutSector =
      listOf(
        HoldingData(
          name = "Google Inc",
          ticker = "GOOGL",
          sector = null,
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingsService.saveHoldings("IITU", testDate, holdingsWithoutSector)
    val savedWithoutSector = etfHoldingRepository.findAll().first()
    expect(savedWithoutSector.sector).toEqual(null)

    val holdingsWithSector =
      listOf(
        HoldingData(
          name = "Google Inc",
          ticker = "GOOGL",
          sector = "Technology",
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingsService.saveHoldings("IITU", testDate.plusDays(1), holdingsWithSector)

    val updatedHolding = etfHoldingRepository.findAll().first()
    expect(updatedHolding.sector).toEqual("Technology")
    expect(updatedHolding.classifiedByModel).toEqual(null)
  }

  @Test
  fun `should not overwrite existing sector from source`() {
    val holdingsWithSector =
      listOf(
        HoldingData(
          name = "Facebook Inc",
          ticker = "FB",
          sector = "Technology",
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingsService.saveHoldings("IITU", testDate, holdingsWithSector)
    val savedWithSector = etfHoldingRepository.findAll().first()
    expect(savedWithSector.sector).toEqual("Technology")

    val holdingsWithDifferentSector =
      listOf(
        HoldingData(
          name = "Facebook Inc",
          ticker = "FB",
          sector = "Communication",
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingsService.saveHoldings("IITU", testDate.plusDays(1), holdingsWithDifferentSector)

    val unchangedHolding = etfHoldingRepository.findAll().first()
    expect(unchangedHolding.sector).toEqual("Technology")
  }

  @Test
  fun `should create separate holdings when same ticker has different company names`() {
    val usCompanyHoldings =
      listOf(
        HoldingData(
          name = "Merck & Co.",
          ticker = "MRK",
          sector = "Healthcare",
          weight = BigDecimal("10.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingsService.saveHoldings("IITU", testDate, usCompanyHoldings)
    expect(etfHoldingRepository.findAll().size).toEqual(1)

    val germanCompanyHoldings =
      listOf(
        HoldingData(
          name = "Merck KGaA",
          ticker = "MRK",
          sector = "Healthcare",
          weight = BigDecimal("8.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingsService.saveHoldings("IITU", testDate.plusDays(1), germanCompanyHoldings)

    val allHoldings = etfHoldingRepository.findAll()
    expect(allHoldings.size).toEqual(2)
    expect(allHoldings.map { it.name }.toSet()).toEqual(setOf("Merck & Co.", "Merck KGaA"))
    expect(allHoldings.all { it.ticker == "MRK" }).toEqual(true)
  }

  @Test
  fun `should reuse existing holding when name and ticker match exactly`() {
    val holdings =
      listOf(
        HoldingData(
          name = "Apple Inc",
          ticker = "AAPL",
          sector = "Technology",
          weight = BigDecimal("15.0"),
          rank = 1,
          logoUrl = null,
        ),
      )
    etfHoldingsService.saveHoldings("IITU", testDate, holdings)
    val originalId = etfHoldingRepository.findAll().first().id

    etfHoldingsService.saveHoldings("IITU", testDate.plusDays(1), holdings)

    val allHoldings = etfHoldingRepository.findAll()
    expect(allHoldings.size).toEqual(1)
    expect(allHoldings.first().id).toEqual(originalId)
  }
}
