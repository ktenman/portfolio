package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INTRADAY_REPLAY_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.MinutePricePoint
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.dto.IntradaySummaryPointDto
import ee.tenman.portfolio.model.metrics.PreparedDay
import ee.tenman.portfolio.model.metrics.PreparedHolding
import ee.tenman.portfolio.repository.InstrumentMinutePriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.pricing.PriceLookup
import ee.tenman.portfolio.service.transaction.TransactionCacheService
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture

@Service
class IntradaySummaryReplayService(
  private val transactionCacheService: TransactionCacheService,
  private val instrumentRepository: InstrumentRepository,
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
    val cached = CompletableFuture.supplyAsync { transactionCacheService.getAllTransactions() }
    val instruments = instrumentRepository.findAllTradedOn(platforms)
    val transactions = transactions(cached.join(), platforms, instruments.associateBy { it.id })
    if (transactions.isEmpty()) return emptyList()
    val times = replayTimes(days)
    val dates = times.map { it.atZone(clock.zone).toLocalDate() }.distinct()
    val holdings = dailySummaryCalculator.holdingsByPrefix(transactions)
    val ids = held(transactions, dates, holdings).ifEmpty { instruments.map { it.id } }
    val changes = CompletableFuture.supplyAsync { instrumentMinutePriceRepository.findPriceChangesAt(ids, times).groupBy { it.capturedAt } }
    val lookup = dailyPriceService.buildLatestPriceLookup(instruments, dates.flatMap(dailySummaryCalculator::pricedDates))
    val prepared = dates.associateWith { prepare(transactions, it, lookup, holdings) }
    return replay(changes.join(), times, prepared, lookup)
  }

  private fun replayTimes(days: Long): List<Instant> {
    val now = Instant.now(clock)
    return timestamps(now.minus(days, ChronoUnit.DAYS), now.truncatedTo(ChronoUnit.MINUTES), bucketSeconds(days))
  }

  private fun held(
    transactions: List<PortfolioTransaction>,
    dates: List<LocalDate>,
    holdings: (List<PortfolioTransaction>) -> List<PreparedHolding>,
  ): List<Long> =
    dates
      .flatMap { date -> holdings(transactions.filter { !it.transactionDate.isAfter(date) }) }
      .filter { it.held }
      .map { it.instrument.id }
      .distinct()

  private fun transactions(
    cached: List<PortfolioTransaction>,
    platforms: List<Platform>,
    instruments: Map<Long, Instrument>,
  ): List<PortfolioTransaction> =
    cached
      .filter { it.platform in platforms }
      .onEach { it.instrument = instruments.getValue(it.instrument.id) }
      .sortedWith(compareBy({ it.transactionDate }, { it.id }))

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
    changes: Map<Instant, List<MinutePricePoint>>,
    times: List<Instant>,
    days: Map<LocalDate, PreparedDay>,
    lookup: PriceLookup,
  ): List<IntradaySummaryPointDto> {
    val latest = HashMap<Long, BigDecimal>()
    return times.mapNotNull { time ->
      changes[time]?.forEach { latest[it.instrumentId] = it.price }
      val date = time.atZone(clock.zone).toLocalDate()
      latest.takeIf { it.isNotEmpty() }?.let {
        dailySummaryCalculator.calculateFromPrepared(days.getValue(date), lookup.pinnedAt(date, it)).toIntradayPointDto(time)
      }
    }
  }

  private fun prepare(
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    lookup: PriceLookup,
    holdings: (List<PortfolioTransaction>) -> List<PreparedHolding>,
  ): PreparedDay = dailySummaryCalculator.prepareDay(transactions.filter { !it.transactionDate.isAfter(date) }, date, lookup, holdings)
}
