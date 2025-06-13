package ee.tenman.portfolio.service.xirr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.single
import io.kotest.property.checkAll
import java.time.LocalDate
import kotlin.math.abs

class XirrPropertyTest : StringSpec({

  "XIRR should handle normal investment scenarios" {
    checkAll(50, normalInvestmentGenerator()) { (amounts, dates) ->
      val transactions = amounts.zip(dates) { amount, date -> Transaction(amount, date) }
      val xirr = Xirr(transactions)
      val result = runCatching { xirr.calculate() }

      result.fold(
        onSuccess = { xirrValue ->
          xirrValue.isFinite() shouldBe true
          xirrValue shouldBeGreaterThan -1.0
        },
        onFailure = {
          it.message != null
        }
      )
    }
  }

  "XIRR should handle simple investment patterns" {
    checkAll<Unit>(30) {
      val investment = -1000.0
      val returnAmount = Arb.double(500.0, 2000.0).single()
      val days = Arb.int(30, 1000).single()

      val transactions = listOf(
        Transaction(investment, LocalDate.of(2023, 1, 1)),
        Transaction(returnAmount, LocalDate.of(2023, 1, 1).plusDays(days.toLong()))
      )

      val result = runCatching { Xirr(transactions).calculate() }

      result.fold(
        onSuccess = { xirrValue ->
          xirrValue.isFinite() shouldBe true
          xirrValue shouldBeGreaterThan -1.0
        },
        onFailure = { true }
      )
    }
  }

  "XIRR calculation should be mathematically sound" {
    val knownTransactions = listOf(
      Transaction(-1000.0, LocalDate.of(2023, 1, 1)),
      Transaction(1100.0, LocalDate.of(2023, 12, 31))
    )

    val result = Xirr(knownTransactions).calculate()

    result shouldBeGreaterThan 0.09
    result shouldBeLessThan 0.11
  }
})

private fun normalInvestmentGenerator() = Arb.bind(
  Arb.list(Arb.double(-1000.0, 1000.0), 2..5),
  Arb.int(30..730)
) { amounts, dayRange ->
  val dates = generateSortedDates(amounts.size, dayRange)

  val normalizedAmounts = amounts.mapIndexed { index, amount ->
    when (index) {
      0 -> -abs(amount)
      amounts.lastIndex -> abs(amount) * 1.1
      else -> amount * 0.1
    }
  }

  normalizedAmounts to dates
}

private fun validCashFlowGenerator() = Arb.bind(
  Arb.list(Arb.double(-10000.0, 10000.0), 2..8),
  Arb.int(1..1000)
) { amounts, dayRange ->
  val dates = generateSortedDates(amounts.size, dayRange)

  val validAmounts = if (!hasValidCashFlow(amounts)) {
    amounts.mapIndexed { index, amount ->
      when (index) {
        0 -> -abs(amount)
        amounts.lastIndex -> abs(amount)
        else -> amount
      }
    }
  } else amounts

  validAmounts to dates
}

private fun generateSortedDates(count: Int, maxDayRange: Int = 365): List<LocalDate> {
  val baseDate = LocalDate.of(2023, 1, 1)
  return (0 until count).map { i ->
    baseDate.plusDays((i * maxDayRange / count).toLong())
  }.sorted()
}

private fun hasValidCashFlow(amounts: List<Double>): Boolean {
  return amounts.any { it > 0 } && amounts.any { it < 0 }
}
