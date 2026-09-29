package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.common.DailyPriceData
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.model.CollectionRunResult
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.createInstrument
import ee.tenman.portfolio.testing.fixture.monitorForTests
import ee.tenman.portfolio.tuleva.TulevaNav
import ee.tenman.portfolio.tuleva.TulevaNavClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class TulevaNavRetrievalJobTest {
  private val instrument = createInstrument(symbol = ISIN, providerName = ProviderName.TULEVA)

  @Test
  fun `should save each Tuleva NAV as the daily close`() {
    val fixture = fixture(listOf(TulevaNav(LocalDate.of(2020, 12, 17), BigDecimal("0.7019"))))
    val prices = slot<Map<LocalDate, DailyPriceData>>()
    fixture.job.execute()
    verify { fixture.dataProcessingUtil.processDailyData(instrument, capture(prices), ProviderName.TULEVA) }
    expect(prices.captured.getValue(LocalDate.of(2020, 12, 17)).close).toEqualNumerically(BigDecimal("0.7019"))
  }

  @Test
  fun `should mark the fund persisted after saving its NAV history`() {
    val fixture = fixture(listOf(TulevaNav(LocalDate.of(2026, 9, 25), BigDecimal("1.4567"))))
    fixture.job.execute()
    expect(fixture.collections.single().persisted).toContainExactly(ISIN)
  }

  @Test
  fun `should mark the fund failed when Tuleva returns no NAV`() {
    val fixture = fixture(emptyList())
    fixture.job.execute()
    expect(fixture.collections.single().failed).toContainExactly(ISIN)
  }

  @Test
  fun `should mark the fund failed when Tuleva returns a nonpositive NAV`() {
    val fixture = fixture(listOf(TulevaNav(LocalDate.of(2026, 9, 25), BigDecimal.ZERO)))
    fixture.job.execute()
    expect(fixture.collections.single().failed).toContainExactly(ISIN)
  }

  private fun fixture(navs: List<TulevaNav>): Fixture {
    val instrumentService = mockk<InstrumentService>()
    val client = mockk<TulevaNavClient>()
    val dataProcessingUtil = mockk<DataProcessingUtil>(relaxed = true)
    val collections = mutableListOf<CollectionRunResult>()
    every { instrumentService.getInstrumentsByProvider(ProviderName.TULEVA) } returns listOf(instrument)
    every { client.getNav(ISIN, any()) } returns navs
    val job =
      TulevaNavRetrievalJob(
        instrumentService,
        client,
        dataProcessingUtil,
        mockk<JobExecutionService>(relaxed = true),
        monitorForTests(collections),
      )
    return Fixture(job, dataProcessingUtil, collections)
  }

  private data class Fixture(
    val job: TulevaNavRetrievalJob,
    val dataProcessingUtil: DataProcessingUtil,
    val collections: List<CollectionRunResult>,
  )

  private companion object {
    const val ISIN = "EE3600001707"
  }
}
