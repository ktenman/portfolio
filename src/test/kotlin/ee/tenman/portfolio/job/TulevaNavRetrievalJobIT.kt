package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
import ee.tenman.portfolio.tuleva.TulevaNavClient
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.wiremock.spring.InjectWireMock
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class TulevaNavRetrievalJobIT {
  @Resource
  private lateinit var tulevaNavClient: TulevaNavClient

  @Resource
  private lateinit var instrumentService: InstrumentService

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var dailyPriceRepository: DailyPriceRepository

  @Resource
  private lateinit var dataProcessingUtil: DataProcessingUtil

  @Resource
  private lateinit var jobExecutionService: JobExecutionService

  @Resource
  private lateinit var collectionMonitor: CollectionMonitorService

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  @Test
  fun `should store the Tuleva NAV as a TULEVA daily close`() {
    wireMockServer.resetAll()
    wireMockServer.stubFor(
      get(urlPathEqualTo("/v1/funds/$ISIN/nav"))
        .withQueryParam("startDate", equalTo("2017-01-01"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""[{"date":"2020-12-17","value":0.7019}]"""),
        ),
    )
    val instrument =
      instrumentRepository.save(
        Instrument(
          symbol = ISIN,
          name = "Tuleva III Samba Pensionifond",
          category = "ETF",
          baseCurrency = "EUR",
          providerName = ProviderName.TULEVA,
        ),
      )
    TulevaNavRetrievalJob(
      instrumentService,
      tulevaNavClient,
      dataProcessingUtil,
      jobExecutionService,
      collectionMonitor,
    ).execute()
    val price = dailyPriceRepository.findByInstrumentAndEntryDate(instrument, LocalDate.of(2020, 12, 17))
    expect(price?.closePrice).notToEqualNull().toEqualNumerically(BigDecimal("0.7019"))
  }

  private companion object {
    const val ISIN = "EE3600001707"
  }
}
