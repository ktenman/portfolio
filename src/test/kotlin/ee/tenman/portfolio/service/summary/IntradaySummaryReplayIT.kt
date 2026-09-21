package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INTRADAY_REPLAY_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.InstrumentMinutePrice
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.InstrumentMinutePriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.transaction.TransactionService
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@IntegrationTest
class IntradaySummaryReplayIT {
  @Resource
  private lateinit var intradaySummaryService: IntradaySummaryService

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var portfolioTransactionRepository: PortfolioTransactionRepository

  @Resource
  private lateinit var instrumentMinutePriceRepository: InstrumentMinutePriceRepository

  @Resource
  private lateinit var transactionService: TransactionService

  @Resource
  private lateinit var redis: StringRedisTemplate

  @Resource
  private lateinit var clock: Clock

  private val selection = listOf(Platform.LIGHTYEAR, Platform.LIGHTYEAR_BUSINESS)
  private val key = "$INTRADAY_REPLAY_CACHE::1:LIGHTYEAR,LIGHTYEAR_BUSINESS"

  @Test
  fun `should serve partial platform summaries through the public service`() {
    seed()
    val points = intradaySummaryService.getPoints(TimeRange.ONE_DAY, selection)
    expect(points.size).toBeGreaterThan(1)
    expect(points.last().totalValue).toEqualNumerically(BigDecimal("2200"))
  }

  @Test
  fun `should share cached points across reordered and duplicated selections`() {
    val trade = seed()
    val original = intradaySummaryService.getPoints(TimeRange.ONE_DAY, selection)
    transactionService.saveTransaction(trade.apply { quantity = BigDecimal("20") })
    val repeated = intradaySummaryService.getPoints(TimeRange.ONE_DAY, listOf(selection[1], selection[0], selection[1]))
    expect(repeated).toEqual(original)
  }

  @Test
  fun `should expire replayed points after one minute`() {
    seed()
    intradaySummaryService.getPoints(TimeRange.ONE_DAY, selection)
    val seconds = redis.getExpire(key, TimeUnit.SECONDS)
    expect(seconds).toBeGreaterThan(50L)
    expect(seconds).toBeLessThanOrEqualTo(60L)
  }

  @Test
  fun `should restate points after expiry even when the filtered transaction cache is stale`() {
    val trade = seed()
    transactionService.getAllTransactions(selection.map { it.name })
    intradaySummaryService.getPoints(TimeRange.ONE_DAY, selection)
    transactionService.saveTransaction(trade.apply { quantity = BigDecimal("20") })
    redis.expire(key, Duration.ZERO)
    val restated = intradaySummaryService.getPoints(TimeRange.ONE_DAY, selection)
    expect(restated.last().totalValue).toEqualNumerically(BigDecimal("3300"))
  }

  private fun seed(): PortfolioTransaction {
    val instrument = instrumentRepository.save(Instrument("TEST", "Replay instrument", "STOCK", "EUR", BigDecimal("999")))
    val trades =
      (selection + Platform.LHV).map { platform ->
      portfolioTransactionRepository.save(
        PortfolioTransaction(
          instrument,
          TransactionType.BUY,
          BigDecimal.TEN,
          BigDecimal("100"),
          LocalDate.now(clock).minusDays(10),
          platform,
        ),
      )
    }
    val now = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES)
    instrumentMinutePriceRepository.save(InstrumentMinutePrice(instrument, now.minus(20, ChronoUnit.MINUTES), BigDecimal("110")))
    return trades.first()
  }
}
