package ee.tenman.portfolio.service.calculation.xirr

import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class XirrTest {
  data class XirrTestCase(
    val description: String,
    val cashFlows: List<CashFlow>,
    val expectedXirr: Double,
  ) {
    override fun toString(): String = description
  }

  companion object {
    private val FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    @JvmStatic
    fun xirrTestCases() =
      listOf(
        XirrTestCase(
          "short period with two transactions",
          listOf(
            CashFlow(-102.09, LocalDate.of(2024, 7, 4)),
            CashFlow(100.0, LocalDate.of(2024, 7, 1)),
          ),
          0.9899995279312134,
        ),
        XirrTestCase(
          "complex set of transactions over multiple years",
          complexCashFlows,
          0.14743321895599365,
        ),
      )

    private val complexCashFlows: List<CashFlow> =
      """
      -22390.95 04.07.2024 103.85 18.04.2019 69.23 18.04.2019 250 23.05.2019 150 23.05.2019 250 26.06.2019
      150 26.06.2019 187.5 24.07.2019 200 24.07.2019 250 21.08.2019 150 21.08.2019 250 25.09.2019
      150 25.09.2019 250 23.10.2019 150 23.10.2019 250 21.11.2019 150 21.11.2019 271.73 13.12.2019
      163.04 13.12.2019 262.5 23.01.2020 157.5 23.01.2020 262.5 20.02.2020 157.5 20.02.2020 157.5 13.03.2020
      262.5 13.03.2020 157.5 15.04.2020 262.5 15.04.2020 157.5 26.05.2020 262.5 26.05.2020 157.5 12.06.2020
      262.5 12.06.2020 157.5 24.07.2020 262.5 24.07.2020 157.5 20.08.2020 262.5 20.08.2020 157.5 24.09.2020
      262.5 24.09.2020 175 19.10.2020 291.66 19.10.2020 175 16.11.2020 291.66 16.11.2020 175 21.12.2020
      291.66 21.12.2020 175 21.01.2021 291.66 21.01.2021 175 18.02.2021 291.66 18.02.2021 187.5 24.03.2021
      312.5 24.03.2021 187.5 21.04.2021 312.5 21.04.2021 187.5 20.05.2021 312.5 20.05.2021 187.5 23.06.2021
      312.5 23.06.2021 187.5 21.07.2021 312.5 21.07.2021 187.5 20.08.2021 312.5 20.08.2021 187.5 16.09.2021
      312.5 16.09.2021
      """.trimIndent()
        .split(Regex("\\s+"))
        .windowed(2, 2)
        .map { (amount, dateStr) ->
          CashFlow(amount.toDouble(), LocalDate.parse(dateStr, FORMATTER))
        }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("xirrTestCases")
  fun `should calculate correct XIRR value when given transactions`(testCase: XirrTestCase) {
    val xirrValue = Xirr(testCase.cashFlows)()

    val tolerance = 1e-14
    expect(Math.abs(xirrValue - testCase.expectedXirr)).toBeLessThan(tolerance)
  }
}
