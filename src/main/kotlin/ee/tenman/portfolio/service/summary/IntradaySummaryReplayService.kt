package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INTRADAY_REPLAY_CACHE
import ee.tenman.portfolio.domain.InstrumentMinutePrice
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.dto.IntradaySummaryPointDto
import ee.tenman.portfolio.repository.InstrumentMinutePriceRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.pricing.PriceLookup
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class IntradaySummaryReplayService(
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val instrumentMinutePriceRepository: InstrumentMinutePriceRepository,
  private val dailyPriceService: DailyPriceService,
  private val dailySummaryCalculator: DailySummaryCalculator,
  private val clock: Clock,
) {
  @Cacheable(
    value = [INTRADAY_REPLAY_CACHE],
    key = "#root.target.selectionKey(#platforms) + ':' + #range.name()",
    unless = "#result.isEmpty()",
  )
  @Transactional(readOnly = true)
  fun getPoints(
    range: TimeRange,
    platforms: List<Platform>,
  ): List<IntradaySummaryPointDto> {
    val now = Instant.now(clock)
    val days = range.intradayDays(now.atZone(clock.zone).toLocalDate()) ?: return emptyList()
    if (platforms.isEmpty()) return emptyList()
    return load(platforms, now, days)
  }

  private fun load(
    platforms: List<Platform>,
    now: Instant,
    days: Long,
  ): List<IntradaySummaryPointDto> {
    val transactions =
      portfolioTransactionRepository
      .findAllByPlatformsWithInstruments(platforms.distinct().sortedBy { it.name })
      .sortedWith(compareBy({ it.transactionDate }, { it.id }))
    if (transactions.isEmpty()) return emptyList()
    val instruments = transactions.map { it.instrument }.distinctBy { it.id }
    val from = now.minus(days, ChronoUnit.DAYS)
    val until = now.truncatedTo(ChronoUnit.MINUTES)
    val captures = instrumentMinutePriceRepository.findForReplay(instruments.map { it.id }, from, until)
    if (captures.isEmpty()) return emptyList()
    val times = timestamps(maxOf(from, captures.first().capturedAt), until, Duration.ofDays(days).seconds / MAX_POINTS)
    return replay(times, captures, transactions, dailyPriceService.buildPriceLookup(instruments))
  }

  fun selectionKey(platforms: List<Platform>): String =
    platforms
    .map { it.name }
    .distinct()
    .sorted()
    .joinToString(",")

  private fun timestamps(
    from: Instant,
    until: Instant,
    width: Long,
  ): List<Instant> {
    val first = Math.floorDiv(from.epochSecond, width) * width
    val last = Math.floorDiv(until.epochSecond, width) * width
    return (first..last step width)
      .map { start -> minOf(Instant.ofEpochSecond(start + width - 1).truncatedTo(ChronoUnit.MINUTES), until) }
      .filter { !it.isBefore(from) }
      .takeLast(MAX_POINTS.toInt())
  }

  private fun replay(
    times: List<Instant>,
    captures: List<InstrumentMinutePrice>,
    transactions: List<PortfolioTransaction>,
    lookup: PriceLookup,
  ): List<IntradaySummaryPointDto> {
    val prices = mutableMapOf<Long, BigDecimal>()
    val dates = mutableMapOf<LocalDate, List<PortfolioTransaction>>()
    val iterator = captures.iterator()
    var capture = iterator.next()
    var available = true
    return times.map { time ->
      while (available && !capture.capturedAt.isAfter(time)) {
        prices[capture.instrument.id] = capture.price
        available = iterator.hasNext()
        if (available) capture = iterator.next()
      }
      val date = time.atZone(clock.zone).toLocalDate()
      val eligible = dates.getOrPut(date) { transactions.filter { !it.transactionDate.isAfter(date) } }
      dailySummaryCalculator.calculateFromTransactions(eligible, date, lookup.pinnedAt(date, prices)).toIntradayPointDto(time)
    }
  }

  companion object {
    private const val MAX_POINTS = 300L
  }
}
