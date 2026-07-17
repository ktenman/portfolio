package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.ecb.EcbClient
import ee.tenman.portfolio.ecb.EcbDailyRate
import ee.tenman.portfolio.service.currency.ExchangeRateService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ExchangeRateRetrievalJobTest {
  private val ecbClient: EcbClient = mockk()
  private val exchangeRateService: ExchangeRateService = mockk(relaxed = true)
  private val jobExecutionService: JobExecutionService = mockk(relaxed = true)
  private val taskScheduler: TaskScheduler = mockk(relaxed = true)
  private val clock: Clock = Clock.fixed(Instant.parse("2026-06-11T17:00:00Z"), ZoneId.of("UTC"))

  private val job =
    ExchangeRateRetrievalJob(
      ecbClient = ecbClient,
      exchangeRateService = exchangeRateService,
      jobExecutionService = jobExecutionService,
      taskScheduler = taskScheduler,
      clock = clock,
    )

  private val header =
    "KEY,FREQ,CURRENCY,CURRENCY_DENOM,EXR_TYPE,EXR_SUFFIX,TIME_PERIOD,OBS_VALUE,OBS_STATUS,OBS_CONF,OBS_PRE_BREAK," +
      "OBS_COM,TIME_FORMAT,BREAKS,COLLECTION,COMPILING_ORG,DISS_ORG,DOM_SER_IDS,PUBL_ECB,PUBL_MU,PUBL_PUBLIC," +
      "UNIT_INDEX_BASE,COMPILATION,COVERAGE,DECIMALS,NAT_TITLE,SOURCE_AGENCY,SOURCE_PUB,TITLE,TITLE_COMPL,UNIT,UNIT_MULT"

  private val csv =
    header + "\n" +
      "EXR.D.GBP.EUR.SP00.A,D,GBP,EUR,SP00,A,2026-06-10,0.86228,A,F,,,P1D,,A,,,,,,,99Q1=100,,,5,,4F0,,title,compl,GBP,0"

  @BeforeEach
  fun stubUsd() {
    every { ecbClient.fetchDailyRates("USD", any(), "csvdata") } returns ""
  }

  @Test
  fun `should backfill from december 2014 when no rates are stored`() {
    every { exchangeRateService.findLatestRateDate(Currency.GBP) } returns null
    every { ecbClient.fetchDailyRates("GBP", "2014-12-01", "csvdata") } returns csv

    job.execute()

    verify(exactly = 1) { ecbClient.fetchDailyRates("GBP", "2014-12-01", "csvdata") }
  }

  @Test
  fun `should schedule initial run five seconds after startup`() {
    job.scheduleInitialRun()

    verify(exactly = 1) { taskScheduler.schedule(any(), Instant.parse("2026-06-11T17:00:05Z")) }
  }

  @Test
  fun `should fetch from latest stored date when rates exist`() {
    every { exchangeRateService.findLatestRateDate(Currency.GBP) } returns LocalDate.of(2026, 6, 10)
    every { ecbClient.fetchDailyRates("GBP", "2026-06-10", "csvdata") } returns csv

    job.execute()

    verify(exactly = 1) { ecbClient.fetchDailyRates("GBP", "2026-06-10", "csvdata") }
  }

  @Test
  fun `should save parsed rates for gbp`() {
    every { exchangeRateService.findLatestRateDate(Currency.GBP) } returns LocalDate.of(2026, 6, 10)
    every { ecbClient.fetchDailyRates("GBP", "2026-06-10", "csvdata") } returns csv

    job.execute()

    verify(exactly = 1) {
      exchangeRateService.saveRates(
        Currency.GBP,
        listOf(EcbDailyRate(LocalDate.of(2026, 6, 10), BigDecimal("0.86228"))),
      )
    }
  }
}
