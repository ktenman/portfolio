package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.ninjasquad.springmockk.SpykBean
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.MinioProperties
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.RemoveObjectArgs
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

  @SpykBean
  private lateinit var minioService: MinioService

  @Resource
  private lateinit var minioClient: MinioClient

  @Resource
  private lateinit var minioProperties: MinioProperties

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  private lateinit var testEtf: Instrument

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
            .withBody(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)),
        ),
    )
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

    etfHoldingsService.saveHoldings("IITU", LocalDate.now(), holdings)

    verify(exactly = 1) { minioService.logoExists("AAPL") }
    verify(exactly = 1) { minioService.uploadLogo("AAPL", any()) }
  }

  @Test
  fun `should not upload logo if it already exists`() {
    val logoUrl = "http://localhost:${wireMockServer.port()}/test-logo.png"
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

    etfHoldingsService.saveHoldings("IITU", LocalDate.now(), holdings)

    verify(atLeast = 1) { minioService.logoExists("MSFT") }
    verify(atLeast = 1) { minioService.uploadLogo("MSFT", any()) }

    val logoExistsBefore = minioService.logoExists("MSFT")
    expect(logoExistsBefore).toEqual(true)

    etfHoldingsService.saveHoldings("IITU", LocalDate.now().plusDays(1), holdings)

    val logoExistsAfter = minioService.logoExists("MSFT")
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

    etfHoldingsService.saveHoldings("IITU", LocalDate.now(), holdings)

    val tslaExists = minioService.logoExists("TSLA")
    expect(tslaExists).toEqual(false)

    val savedHoldings = etfHoldingRepository.findAll()
    expect(savedHoldings.size).toEqual(1)
    expect(savedHoldings.first().ticker).toEqual("TSLA")
  }

  @Test
  fun `should handle multiple holdings with mixed logo states`() {
    val logoUrl = "http://localhost:${wireMockServer.port()}/test-logo.png"

    minioService.uploadLogo("NVDA", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

    val nvdaExistsBefore = minioService.logoExists("NVDA")
    expect(nvdaExistsBefore).toEqual(true)

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

    etfHoldingsService.saveHoldings("IITU", LocalDate.now(), holdings)

    val nvdaExistsAfter = minioService.logoExists("NVDA")
    expect(nvdaExistsAfter).toEqual(true)

    val amznExists = minioService.logoExists("AMZN")
    expect(amznExists).toEqual(true)

    val metaExists = minioService.logoExists("META")
    expect(metaExists).toEqual(false)

    val savedHoldings = etfHoldingRepository.findAll()
    expect(savedHoldings.size).toEqual(3)
  }
}
