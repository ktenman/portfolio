package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.dto.AnnualWindowDto
import ee.tenman.portfolio.dto.AnnualWindowsDto
import ee.tenman.portfolio.model.InstrumentSnapshot
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.service.instrument.InstrumentSnapshotService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.math.pow

@Service
class PortfolioAnnualWindowService(
  private val instrumentSnapshotService: InstrumentSnapshotService,
  private val dailyPriceRepository: DailyPriceRepository,
  private val clock: Clock,
) {
  private val windows: List<XirrWindowDefinition> =
    listOf(
      XirrWindowDefinition("1M", Period.ofMonths(1)),
      XirrWindowDefinition("3M", Period.ofMonths(3)),
      XirrWindowDefinition("6M", Period.ofMonths(6)),
      XirrWindowDefinition("1Y", Period.ofYears(1)),
      XirrWindowDefinition("2Y", Period.ofYears(2)),
      XirrWindowDefinition("3Y", Period.ofYears(3)),
    )

  @Transactional(readOnly = true)
  fun calculate(platforms: List<Platform>?): AnnualWindowsDto {
    val today = LocalDate.now(clock)
    val held =
      instrumentSnapshotService
        .getAllSnapshots(platforms?.map { it.name })
        .filter { it.quantity > BigDecimal.ZERO && (it.instrument.currentPrice ?: BigDecimal.ZERO) > BigDecimal.ZERO }
    val totalCurrentValue = held.sumOf { it.currentValue }
    if (held.isEmpty() || totalCurrentValue <= BigDecimal.ZERO) {
      return AnnualWindowsDto(windows.map { notAvailable(it.label) })
    }
    val rows = windows.map { window -> calculateWindow(window, today, held, totalCurrentValue) }
    return AnnualWindowsDto(rows)
  }

  private fun calculateWindow(
    window: XirrWindowDefinition,
    today: LocalDate,
    held: List<InstrumentSnapshot>,
    totalCurrentValue: BigDecimal,
  ): AnnualWindowDto {
    val targetStart = today.minus(window.length)
    val perInstrument =
      held.mapNotNull { snapshot ->
        priceForOpening(snapshot.instrument, targetStart)?.let { (date, price) ->
          AnnualOpeningQuote(snapshot, date, price)
        }
      }
    val effectiveStart = perInstrument.maxOfOrNull { it.priceDate }
    if (perInstrument.size != held.size ||
      effectiveStart == null ||
      effectiveStart.isAfter(targetStart) ||
      !effectiveStart.isBefore(today)
    ) {
      return notAvailable(window.label)
    }
    val portfolioValueAtStart =
      perInstrument.sumOf { quote -> quote.snapshot.quantity.multiply(quote.price) }
    val days = ChronoUnit.DAYS.between(effectiveStart, today).toDouble()
    val totalReturn =
      if (portfolioValueAtStart > BigDecimal.ZERO) {
        totalCurrentValue.toDouble() / portfolioValueAtStart.toDouble()
      } else {
        0.0
      }
    if (days < MIN_DAYS || totalReturn <= 0.0) return notAvailable(window.label)
    val annualized = totalReturn.pow(DAYS_PER_YEAR / days) - 1.0
    return AnnualWindowDto(
      period = window.label,
      fromDate = effectiveStart,
      annualReturn = BigDecimal(annualized).setScale(SCALE, RoundingMode.HALF_UP),
    )
  }

  private fun priceForOpening(
    instrument: Instrument,
    targetStart: LocalDate,
  ): Pair<LocalDate, BigDecimal>? {
    val onOrBefore =
      dailyPriceRepository.findFirstByInstrumentAndEntryDateLessThanEqualOrderByEntryDateDesc(instrument, targetStart)
    if (onOrBefore != null) return onOrBefore.entryDate to onOrBefore.closePrice
    val earliest = dailyPriceRepository.findFirstByInstrumentOrderByEntryDateAsc(instrument) ?: return null
    return earliest.entryDate to earliest.closePrice
  }

  private fun notAvailable(label: String) = AnnualWindowDto(period = label, fromDate = null, annualReturn = null)

  companion object {
    private const val SCALE = 6
    private const val DAYS_PER_YEAR = 365.25
    private const val MIN_DAYS = 7L
  }
}
