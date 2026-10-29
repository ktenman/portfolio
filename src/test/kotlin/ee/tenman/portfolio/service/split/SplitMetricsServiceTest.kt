package ee.tenman.portfolio.service.split

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.InstrumentSplit
import ee.tenman.portfolio.domain.JobExecution
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.InstrumentSplitRepository
import ee.tenman.portfolio.repository.JobExecutionRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SplitMetricsServiceTest {
  private val instrumentSplitRepository = mockk<InstrumentSplitRepository>()

  private val instrumentRepository = mockk<InstrumentRepository>()

  private val jobExecutionRepository = mockk<JobExecutionRepository>()

  private val clock = Clock.fixed(Instant.parse("2026-10-05T06:00:00Z"), ZoneId.of("Europe/Tallinn"))

  private val bnke = Instrument("BNKE:PAR:EUR", "Amundi Euro Stoxx Banks UCITS ETF Acc", "ETF", "EUR", BigDecimal("4.44"))

  private fun refreshed(
    splits: List<InstrumentSplit> = emptyList(),
    instruments: List<Instrument> = emptyList(),
    status: JobStatus? = null,
  ): SimpleMeterRegistry {
    every { instrumentSplitRepository.findPending(LocalDate.of(2026, 10, 5)) } returns splits
    every { instrumentRepository.findAll() } returns instruments
    every { jobExecutionRepository.findFirstByJobNameOrderByStartTimeDesc("SplitCheckJob") } returns
      status?.let { JobExecution("SplitCheckJob", Instant.EPOCH, Instant.EPOCH, 0, it) }
    val registry = SimpleMeterRegistry()
    SplitMetricsService(instrumentSplitRepository, instrumentRepository, jobExecutionRepository, clock)
      .also { it.bindTo(registry) }
      .refresh()
    return registry
  }

  @Test
  fun `should export a pending split with its instrument, ratio, effective date and source`() {
    val source = "https://www.investegate.co.uk/announcement/9804833"
    val split = InstrumentSplit(bnke, "1:80", LocalDate.of(2026, 10, 27), source)
    val gauge =
      refreshed(splits = listOf(split))
        .get("portfolio.split.announced")
        .tags("instrument", "BNKE:PAR:EUR", "ratio", "1:80", "effective", "2026-10-27", "source", source)
    expect(gauge.gauge().value()).toEqual(1.0)
  }

  @Test
  fun `should export no split when none is pending`() {
    expect(refreshed().find("portfolio.split.announced").gauges()).toBeEmpty()
  }

  @Test
  fun `should export the current price of an instrument`() {
    val registry = refreshed(instruments = listOf(bnke))
    expect(
      registry
      .get("portfolio.instrument.price")
      .tags("instrument", "BNKE:PAR:EUR")
      .gauge()
      .value(),
        ).toEqual(4.44)
  }

  @Test
  fun `should leave cash and unpriced instruments out of the prices`() {
    val cash = Instrument("EUR", "Euro", "CASH", "EUR", BigDecimal.ONE)
    val unpriced = Instrument("LSMC:GER:EUR", "Amundi MSCI Semiconductors UCITS ETF", "ETF", "EUR")
    val gauges = refreshed(instruments = listOf(bnke, cash, unpriced)).find("portfolio.instrument.price").gauges()
    expect(gauges.map { it.id.getTag("instrument") }).toContainExactly("BNKE:PAR:EUR")
  }

  @Test
  fun `should report a failed split check`() {
    expect(refreshed(status = JobStatus.FAILURE).get("portfolio.split.check.failed").gauge().value()).toEqual(1.0)
  }

  @Test
  fun `should not report a split check that succeeded`() {
    expect(refreshed(status = JobStatus.SUCCESS).get("portfolio.split.check.failed").gauge().value()).toEqual(0.0)
  }
}
