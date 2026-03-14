package ee.tenman.portfolio.service.comparison

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.dto.ComparisonDataPointDto
import ee.tenman.portfolio.dto.ComparisonResponse
import ee.tenman.portfolio.dto.InstrumentComparisonDto
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import kotlin.math.roundToInt

@Service
class InstrumentComparisonService(
  private val dailyPriceRepository: DailyPriceRepository,
  private val instrumentRepository: InstrumentRepository,
  private val clock: Clock,
) {
  companion object {
    private const val MAX_DATA_POINTS = 250
    private val HUNDRED = BigDecimal(100)
    private val YEAR_PERIOD_PATTERN = Regex("""(\d+)Y""")
  }

  fun getComparisonData(
    instrumentIds: List<Long>,
    period: String,
  ): ComparisonResponse {
    val today = LocalDate.now(clock)
    val startDate = resolveStartDate(period, today)
    val instruments = instrumentRepository.findAllById(instrumentIds).associateBy { it.id!! }
    val allPrices = dailyPriceRepository.findAllByInstrumentIdInAndEntryDateBetween(instrumentIds, startDate, today)
    val pricesByInstrument = allPrices.groupBy { it.instrument.id!! }
    val commonStartDate = findCommonStartDate(pricesByInstrument)
    val comparisons =
      instrumentIds.mapNotNull { id ->
        val instrument = instruments[id] ?: return@mapNotNull null
        buildComparison(instrument, pricesByInstrument[id], commonStartDate)
      }
    return ComparisonResponse(instruments = comparisons, startDate = commonStartDate ?: startDate, endDate = today)
  }

  private fun buildComparison(
    instrument: Instrument,
    prices: List<DailyPrice>?,
    commonStartDate: LocalDate?,
  ): InstrumentComparisonDto? {
    if (prices.isNullOrEmpty()) return null
    val filtered = if (commonStartDate != null) prices.filter { !it.entryDate.isBefore(commonStartDate) } else prices
    if (filtered.isEmpty()) return null
    val normalized = normalizeToPercentage(filtered, filtered.first().closePrice)
    return InstrumentComparisonDto(
      instrumentId = instrument.id!!,
      symbol = instrument.symbol,
      name = instrument.name,
      currentPrice = instrument.currentPrice,
      totalChangePercent = normalized.last().percentageChange,
      dataPoints = sampleDataPoints(normalized),
    )
  }

  internal fun resolveStartDate(
    period: String,
    today: LocalDate,
  ): LocalDate {
    val upper = period.uppercase()
    val yearMatch = YEAR_PERIOD_PATTERN.matchEntire(upper)
    if (yearMatch != null) return today.minusYears(yearMatch.groupValues[1].toLong())
    return when (upper) {
      "1M" -> today.minusMonths(1)
      "6M" -> today.minusMonths(6)
      "YTD" -> LocalDate.of(today.year, 1, 1)
      "MAX" -> LocalDate.of(2000, 1, 1)
      else -> today.minusYears(1)
    }
  }

  private fun findCommonStartDate(pricesByInstrument: Map<Long, List<DailyPrice>>): LocalDate? =
    pricesByInstrument.values
      .mapNotNull { prices -> prices.minByOrNull { it.entryDate }?.entryDate }
      .maxOrNull()

  private fun normalizeToPercentage(
    prices: List<DailyPrice>,
    basePrice: BigDecimal,
  ): List<ComparisonDataPointDto> =
    prices.map { price ->
      ComparisonDataPointDto(
        date = price.entryDate,
        percentageChange = calculatePercentageChange(price.closePrice, basePrice),
      )
    }

  private fun calculatePercentageChange(
    current: BigDecimal,
    base: BigDecimal,
  ): Double {
    if (base.signum() == 0) return 0.0
    return current
      .subtract(base)
      .multiply(HUNDRED)
      .divide(base, 4, RoundingMode.HALF_UP)
      .toDouble()
  }

  private fun sampleDataPoints(dataPoints: List<ComparisonDataPointDto>): List<ComparisonDataPointDto> {
    if (dataPoints.size <= MAX_DATA_POINTS) return dataPoints
    val step = (dataPoints.size - 1).toDouble() / (MAX_DATA_POINTS - 1)
    return List(MAX_DATA_POINTS) { i ->
      dataPoints[(i * step).roundToInt().coerceAtMost(dataPoints.lastIndex)]
    }
  }
}
