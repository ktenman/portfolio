package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INTRADAY_REPLAY_CACHE
import ee.tenman.portfolio.domain.InstrumentMinutePrice
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.dto.IntradaySummaryPointDto
import ee.tenman.portfolio.model.metrics.PreparedDay
import ee.tenman.portfolio.repository.InstrumentMinutePriceRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.pricing.PriceLookup
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
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
    key = "#days + ':' + #platforms",
    unless = "#result.isEmpty()",
  )
  @Transactional(readOnly = true)
  fun getPoints(
    days: Long,
    platforms: List<Platform>,
  ): List<IntradaySummaryPointDto> {
    val transactions =
      portfolioTransactionRepository
      .findAllByPlatformsWithInstruments(platforms)
      .sortedWith(compareBy({ it.transactionDate }, { it.id }))
    if (transactions.isEmpty()) return emptyList()
    val instruments = transactions.map { it.instrument }.distinctBy { it.id }
    val now = Instant.now(clock)
    val from = now.minus(days, ChronoUnit.DAYS)
    val until = now.truncatedTo(ChronoUnit.MINUTES)
    val captures = instrumentMinutePriceRepository.findForReplay(instruments.map { it.id }, from, until)
    if (captures.isEmpty()) return emptyList()
    val times = timestamps(maxOf(from, captures.first().capturedAt), until, bucketSeconds(days))
    val dates = times.map { it.atZone(clock.zone).toLocalDate() }.distinct().flatMap(dailySummaryCalculator::pricedDates)
    return replay(times, captures, transactions, dailyPriceService.buildLatestPriceLookup(instruments, dates))
  }

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
      .takeLast(MAX_INTRADAY_POINTS)
  }

  private fun replay(
    times: List<Instant>,
    captures: List<InstrumentMinutePrice>,
    transactions: List<PortfolioTransaction>,
    lookup: PriceLookup,
  ): List<IntradaySummaryPointDto> {
    val prices = mutableMapOf<Long, BigDecimal>()
    val days = mutableMapOf<LocalDate, PreparedDay>()
    var next = 0
    return times.map { time ->
      while (next < captures.size && !captures[next].capturedAt.isAfter(time)) {
        prices[captures[next].instrument.id] = captures[next].price
        next++
      }
      val date = time.atZone(clock.zone).toLocalDate()
      val day = days.getOrPut(date) { prepare(transactions, date, lookup) }
      dailySummaryCalculator.calculateFromPrepared(day, lookup.pinnedAt(date, prices)).toIntradayPointDto(time)
    }
  }

  private fun prepare(
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    lookup: PriceLookup,
  ): PreparedDay = dailySummaryCalculator.prepareDay(transactions.filter { !it.transactionDate.isAfter(date) }, date, lookup)
}
