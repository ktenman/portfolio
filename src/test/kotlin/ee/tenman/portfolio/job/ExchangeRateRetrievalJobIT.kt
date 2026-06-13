package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.ecb.EcbClient
import ee.tenman.portfolio.ecb.EcbDailyRate
import ee.tenman.portfolio.repository.ExchangeRateRepository
import ee.tenman.portfolio.service.currency.ExchangeRateService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.wiremock.spring.InjectWireMock
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class ExchangeRateRetrievalJobIT {
  @Resource
  private lateinit var ecbClient: EcbClient

  @Resource
  private lateinit var exchangeRateService: ExchangeRateService

  @Resource
  private lateinit var jobExecutionService: JobExecutionService

  @Resource
  private lateinit var exchangeRateRepository: ExchangeRateRepository

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  private val header =
    "KEY,FREQ,CURRENCY,CURRENCY_DENOM,EXR_TYPE,EXR_SUFFIX,TIME_PERIOD,OBS_VALUE,OBS_STATUS,OBS_CONF,OBS_PRE_BREAK," +
      "OBS_COM,TIME_FORMAT,BREAKS,COLLECTION,COMPILING_ORG,DISS_ORG,DOM_SER_IDS,PUBL_ECB,PUBL_MU,PUBL_PUBLIC," +
      "UNIT_INDEX_BASE,COMPILATION,COVERAGE,DECIMALS,NAT_TITLE,SOURCE_AGENCY,SOURCE_PUB,TITLE,TITLE_COMPL,UNIT,UNIT_MULT"

  @BeforeEach
  fun setUp() {
    wireMockServer.resetAll()
  }

  @Test
  fun `should fetch rates through feign and persist them when no rates are stored`() {
    stubEcb("2014-12-01", listOf(row("2026-06-09", "0.8634"), row("2026-06-10", "0.86228")))

    job().execute()

    val rates = storedRates(LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 10))
    expect(rates).toHaveSize(2)
    expect(rates.first { it.entryDate == LocalDate.of(2026, 6, 9) }.rate).toEqualNumerically(BigDecimal("0.8634"))
    expect(rates.first { it.entryDate == LocalDate.of(2026, 6, 10) }.rate).toEqualNumerically(BigDecimal("0.86228"))
  }

  @Test
  fun `should refetch from latest stored date and upsert the overlapping rate`() {
    exchangeRateService.saveRates(Currency.GBP, listOf(EcbDailyRate(LocalDate.of(2026, 6, 10), BigDecimal("0.9"))))
    stubEcb("2026-06-10", listOf(row("2026-06-10", "0.86228"), row("2026-06-11", "0.87015")))

    job().execute()

    val rates = storedRates(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11))
    expect(rates).toHaveSize(2)
    expect(rates.first { it.entryDate == LocalDate.of(2026, 6, 10) }.rate).toEqualNumerically(BigDecimal("0.86228"))
    expect(rates.first { it.entryDate == LocalDate.of(2026, 6, 11) }.rate).toEqualNumerically(BigDecimal("0.87015"))
  }

  private fun job(): ExchangeRateRetrievalJob =
    ExchangeRateRetrievalJob(ecbClient, exchangeRateService, jobExecutionService, ThreadPoolTaskScheduler())

  private fun stubEcb(
    startPeriod: String,
    rows: List<String>,
  ) {
    wireMockServer.stubFor(
      get(urlPathEqualTo("/service/data/EXR/D.GBP.EUR.SP00.A"))
        .withQueryParam("startPeriod", equalTo(startPeriod))
        .withQueryParam("format", equalTo("csvdata"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "text/csv")
            .withBody((listOf(header) + rows).joinToString("\n")),
        ),
    )
  }

  private fun row(
    date: String,
    value: String,
  ): String =
    "EXR.D.GBP.EUR.SP00.A,D,GBP,EUR,SP00,A,$date,$value,A,F,,,P1D,,A,,,,,,,99Q1=100,,,5,,4F0,," +
      "Pound sterling/Euro ECB reference exchange rate," +
      "\"ECB reference exchange rate, Pound sterling/Euro, 2.15 pm (C.E.T.)\",GBP,0"

  private fun storedRates(
    from: LocalDate,
    to: LocalDate,
  ) = exchangeRateRepository.findAllByBaseCurrencyAndQuoteCurrencyAndEntryDateBetween(Currency.EUR, Currency.GBP, from, to)
}
