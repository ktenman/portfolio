package ee.tenman.portfolio.service

import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class CalculatorService(
  private val dailyPriceService: DailyPriceService,
  private val instrumentService: InstrumentService
) {

  private val baseMonthlyInvestment = BigDecimal("10000.00")

  fun calculateXirr(): BigDecimal {
    val qdve = instrumentService.findInstrument("QDVE:GER:EUR")
    val startDate = LocalDate.of(2000, 1, 1)
    val endDate = LocalDate.now()
    val dailyPrices = dailyPriceService.findAllDailyPrices(qdve, startDate, endDate)

    var totalBoughtStocks = BigDecimal.ZERO
    var lastMonthDate: LocalDate? = null

    val transactions = dailyPrices.mapNotNull { dailyPrice ->
      if (lastMonthDate == null || ChronoUnit.MONTHS.between(lastMonthDate, dailyPrice.entryDate) >= 1) {
        val stocksCount = baseMonthlyInvestment.divide(dailyPrice.closePrice, RoundingMode.DOWN)
        totalBoughtStocks += stocksCount
        lastMonthDate = dailyPrice.entryDate
        Transaction(-baseMonthlyInvestment.toDouble(), dailyPrice.entryDate)
      } else null
    } + Transaction(
      dailyPrices.last().closePrice.multiply(totalBoughtStocks).toDouble(),
      dailyPrices.last().entryDate
    )

    return Xirr(transactions).calculate().toBigDecimal().setScale(6, RoundingMode.HALF_UP)
  }
}
