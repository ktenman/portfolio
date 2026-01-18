package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class XirrDampingTest {
  private val fixedInstant = Instant.parse("2024-01-15T10:00:00Z")
  private val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
  private val calculationDate: LocalDate = LocalDate.ofInstant(fixedInstant, ZoneId.of("UTC"))
  private lateinit var xirrCalculationService: XirrCalculationService

  @BeforeEach
  fun setUp() {
    xirrCalculationService = XirrCalculationService(clock)
  }

  @Nested
  inner class DampingThresholdBehavior {
    @Test
    fun `should not damp XIRR below threshold`() {
      val cashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(1)),
          CashFlow(1100.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).notToEqualNull()
      expect(result!!).toBeLessThanOrEqualTo(0.25)
    }

    @Test
    fun `should not damp negative XIRR`() {
      val cashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(1)),
          CashFlow(800.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).notToEqualNull()
      expect(result!!).toBeLessThan(0.0)
    }

    @Test
    fun `should damp high XIRR for short investment period`() {
      val cashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusDays(60)),
          CashFlow(2000.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).notToEqualNull()
      expect(result!!).toBeGreaterThanOrEqualTo(0.25)
      expect(result).toBeLessThan(1.0)
    }
  }

  @Nested
  inner class InvestmentAgeBehavior {
    @Test
    fun `should return full XIRR for 10 year investment`() {
      val cashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(10)),
          CashFlow(5000.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).notToEqualNull()
      expect(result!!).toBeGreaterThan(0.15)
    }

    @Test
    fun `should apply moderate damping for 5 year investment`() {
      val cashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(5)),
          CashFlow(5000.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).notToEqualNull()
      expect(result!!).toBeGreaterThan(0.25)
    }

    @Test
    fun `should apply heavy damping for 1 year investment with high return`() {
      val cashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(1)),
          CashFlow(5000.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).notToEqualNull()
      expect(result!!).toBeGreaterThanOrEqualTo(0.25)
      expect(result).toBeLessThan(0.50)
    }

    @Test
    fun `should return null for investment shorter than minimum days`() {
      val cashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusDays(15)),
          CashFlow(1500.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).toEqual(null)
    }
  }

  @Nested
  inner class DampingProgressionBehavior {
    @Test
    fun `should apply less damping as investment age increases for same raw XIRR`() {
      val shortTermCashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(1)),
          CashFlow(5000.0, calculationDate),
        )
      val longTermCashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(5)),
          CashFlow(5000.0, calculationDate),
        )
      val shortTermResult = xirrCalculationService.calculateAdjustedXirr(shortTermCashFlows, calculationDate)
      val longTermResult = xirrCalculationService.calculateAdjustedXirr(longTermCashFlows, calculationDate)
      expect(shortTermResult).notToEqualNull()
      expect(longTermResult).notToEqualNull()
      expect(longTermResult!!).toBeGreaterThan(shortTermResult!!)
    }

    @Test
    fun `should reach full damping factor at 10 years`() {
      val cashFlows10Years =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(10)),
          CashFlow(2000.0, calculationDate),
        )
      val result10Years = xirrCalculationService.calculateAdjustedXirr(cashFlows10Years, calculationDate)
      expect(result10Years).notToEqualNull()
      expect(result10Years!!).toBeGreaterThan(0.0)
    }

    @Test
    fun `should return higher damped XIRR for longer investment with high raw return`() {
      val cashFlows2Years =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(2)),
          CashFlow(5000.0, calculationDate),
        )
      val cashFlows5Years =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(5)),
          CashFlow(5000.0, calculationDate),
        )
      val result2Years = xirrCalculationService.calculateAdjustedXirr(cashFlows2Years, calculationDate)
      val result5Years = xirrCalculationService.calculateAdjustedXirr(cashFlows5Years, calculationDate)
      expect(result2Years).notToEqualNull()
      expect(result5Years).notToEqualNull()
      expect(result5Years!!).toBeGreaterThan(result2Years!!)
    }
  }

  @Nested
  inner class EdgeCases {
    @Test
    fun `should handle exactly minimum days for XIRR calculation`() {
      val minDays = (365.25 / 12).toLong() + 1
      val cashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusDays(minDays)),
          CashFlow(1200.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).notToEqualNull()
    }

    @Test
    fun `should handle multiple outflows with weighted age calculation`() {
      val cashFlows =
        listOf(
          CashFlow(-500.0, calculationDate.minusYears(5)),
          CashFlow(-500.0, calculationDate.minusYears(1)),
          CashFlow(2000.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).notToEqualNull()
      expect(result!!).toBeGreaterThan(0.0)
    }

    @Test
    fun `should handle XIRR exactly at threshold boundary`() {
      val cashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(1)),
          CashFlow(1250.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).notToEqualNull()
    }

    @Test
    fun `should handle total loss scenario`() {
      val cashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(1)),
          CashFlow(100.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).notToEqualNull()
      expect(result!!).toBeLessThan(0.0)
    }

    @Test
    fun `should cap XIRR at maximum bound after damping`() {
      val cashFlows =
        listOf(
          CashFlow(-100.0, calculationDate.minusYears(10)),
          CashFlow(1000000.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).notToEqualNull()
      expect(result!!).toBeLessThanOrEqualTo(10.0)
    }

    @Test
    fun `should handle only outflows returning null`() {
      val cashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(1)),
          CashFlow(-500.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).toEqual(null)
    }
  }

  @Nested
  inner class CubicDampingBehavior {
    @Test
    fun `should apply stronger damping for shorter investment periods`() {
      val cashFlows1Year =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(1)),
          CashFlow(10000.0, calculationDate),
        )
      val cashFlows5Year =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(5)),
          CashFlow(10000.0, calculationDate),
        )
      val result1Year = xirrCalculationService.calculateAdjustedXirr(cashFlows1Year, calculationDate)
      val result5Year = xirrCalculationService.calculateAdjustedXirr(cashFlows5Year, calculationDate)
      expect(result1Year).notToEqualNull()
      expect(result5Year).notToEqualNull()
      expect(result5Year!!).toBeGreaterThan(result1Year!!)
    }

    @Test
    fun `should preserve relative ordering of returns after damping`() {
      val lowReturnCashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(2)),
          CashFlow(1500.0, calculationDate),
        )
      val highReturnCashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusYears(2)),
          CashFlow(3000.0, calculationDate),
        )
      val lowResult = xirrCalculationService.calculateAdjustedXirr(lowReturnCashFlows, calculationDate)
      val highResult = xirrCalculationService.calculateAdjustedXirr(highReturnCashFlows, calculationDate)
      expect(lowResult).notToEqualNull()
      expect(highResult).notToEqualNull()
      expect(highResult!!).toBeGreaterThan(lowResult!!)
    }

    @Test
    fun `should floor damped XIRR at threshold for high returns with short periods`() {
      val cashFlows =
        listOf(
          CashFlow(-1000.0, calculationDate.minusDays(60)),
          CashFlow(5000.0, calculationDate),
        )
      val result = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
      expect(result).notToEqualNull()
      expect(result!!).toBeGreaterThanOrEqualTo(0.25)
    }
  }
}
