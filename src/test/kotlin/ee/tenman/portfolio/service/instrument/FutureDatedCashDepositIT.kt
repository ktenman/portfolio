package ee.tenman.portfolio.service.instrument

import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import com.ninjasquad.springmockk.MockkBean
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.summary.SummaryService
import ee.tenman.portfolio.service.transaction.TransactionService
import ee.tenman.portfolio.testing.fixture.TransactionFixtures
import ee.tenman.portfolio.testing.fixture.TransactionFixtures.ZERO_COMMISSION
import io.mockk.every
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val LAST_MINUTES_BEFORE_THE_DEPOSIT = Instant.parse("2026-10-31T21:30:00Z")
private val FIRST_MINUTES_OF_THE_DEPOSIT = Instant.parse("2026-10-31T22:30:00Z")

@IntegrationTest
class FutureDatedCashDepositIT {
  @Resource
  private lateinit var instrumentSnapshotService: InstrumentSnapshotService

  @Resource
  private lateinit var summaryService: SummaryService

  @Resource
  private lateinit var transactionService: TransactionService

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var transactionRepository: PortfolioTransactionRepository

  @MockkBean
  lateinit var clock: Clock

  @BeforeEach
  fun setUp() {
    val cash = instrumentRepository.save(TransactionFixtures.createCashInstrument(name = "Eurode sularaha", id = 0L))
    deposit(cash, BigDecimal("630"), LocalDate.of(2026, 10, 1))
    deposit(cash, BigDecimal("8000"), LocalDate.of(2026, 11, 1))
    every { clock.zone } returns ZoneId.of("Europe/Tallinn")
  }

  @Test
  fun `should keep a cash deposit off the instruments page until its date`() {
    every { clock.instant() } returns LAST_MINUTES_BEFORE_THE_DEPOSIT

    expect(instrumentSnapshotService.getAllSnapshots().single().quantity).toEqualNumerically(BigDecimal("630"))
  }

  @Test
  fun `should count a cash deposit on the instruments page from the start of its date`() {
    every { clock.instant() } returns FIRST_MINUTES_OF_THE_DEPOSIT

    expect(instrumentSnapshotService.getAllSnapshots().single().quantity).toEqualNumerically(BigDecimal("8630"))
  }

  @Test
  fun `should keep a cash deposit out of the summary value until its date`() {
    every { clock.instant() } returns LAST_MINUTES_BEFORE_THE_DEPOSIT

    expect(summaryService.getCurrentDaySummary().totalValue).toEqualNumerically(BigDecimal("630"))
  }

  @Test
  fun `should count a cash deposit in the summary value from the start of its date`() {
    every { clock.instant() } returns FIRST_MINUTES_OF_THE_DEPOSIT

    expect(summaryService.getCurrentDaySummary().totalValue).toEqualNumerically(BigDecimal("8630"))
  }

  @Test
  fun `should leave total profit at zero when a cash deposit takes effect`() {
    every { clock.instant() } returns FIRST_MINUTES_OF_THE_DEPOSIT

    expect(summaryService.getCurrentDaySummary().totalProfit).toEqualNumerically(BigDecimal.ZERO)
  }

  @Test
  fun `should read transactions up to and including the until date`() {
    expect(transactionService.getAllTransactions(null, null, LocalDate.of(2026, 10, 1))).toHaveSize(1)
  }

  private fun deposit(
    cash: Instrument,
    quantity: BigDecimal,
    date: LocalDate,
  ) {
    transactionRepository.save(
      TransactionFixtures.createBuyTransaction(cash, quantity, BigDecimal.ONE, date, Platform.LIGHTYEAR_BUSINESS, ZERO_COMMISSION),
    )
  }
}
