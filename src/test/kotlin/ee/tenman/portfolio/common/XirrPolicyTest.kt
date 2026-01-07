package ee.tenman.portfolio.common

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class XirrPolicyTest {
  @Nested
  inner class DampingFactorTests {
    @Test
    fun `should return zero for holdings below minimum period`() {
      val factor = XirrPolicy.dampingFactor(29)

      expect(factor).toEqualNumerically(BigDecimal.ZERO)
    }

    @Test
    fun `should return one for holdings at or above full weight period`() {
      val factor = XirrPolicy.dampingFactor(61)

      expect(factor).toEqualNumerically(BigDecimal.ONE)
    }

    @Test
    fun `should return one for holdings well above full weight period`() {
      val factor = XirrPolicy.dampingFactor(365)

      expect(factor).toEqualNumerically(BigDecimal.ONE)
    }

    @Test
    fun `should return proportional factor for holdings between periods`() {
      val factor = XirrPolicy.dampingFactor(45)
      val expected = 45.0 / 61.0

      expect(factor.toDouble()).toBeGreaterThan(expected - 0.01)
      expect(factor.toDouble()).toBeLessThan(expected + 0.01)
    }
  }

  @Nested
  inner class EligibilityTests {
    @Test
    fun `should be eligible when weighted days exceed minimum`() {
      val eligible = XirrPolicy.isEligibleForCalculation(35.0)

      expect(eligible).toEqual(true)
    }

    @Test
    fun `should not be eligible when weighted days below minimum`() {
      val eligible = XirrPolicy.isEligibleForCalculation(25.0)

      expect(eligible).toEqual(false)
    }
  }

  @Nested
  inner class ApplyDampingTests {
    @Test
    fun `should apply full damping when weighted days exceed threshold`() {
      val result = XirrPolicy.applyDamping(0.15, 100.0)

      expect(result).toEqual(0.15)
    }

    @Test
    fun `should apply partial damping when weighted days below threshold`() {
      val result = XirrPolicy.applyDamping(0.20, 30.0)
      val expectedFactor = 30.0 / XirrPolicy.FULL_DAMPING_DAYS
      val expected = 0.20 * expectedFactor

      expect(result).toEqual(expected)
    }

    @Test
    fun `should clamp XIRR to maximum bound`() {
      val result = XirrPolicy.applyDamping(15.0, 100.0)

      expect(result).toEqual(10.0)
    }

    @Test
    fun `should clamp XIRR to minimum bound`() {
      val result = XirrPolicy.applyDamping(-15.0, 100.0)

      expect(result).toEqual(-10.0)
    }
  }

  @Nested
  inner class PolicyConstantsTests {
    @Test
    fun `should have correct minimum holding period`() {
      expect(XirrPolicy.minimumHoldingPeriod.toDays()).toEqual(30)
    }

    @Test
    fun `should have correct full weight period`() {
      expect(XirrPolicy.fullWeightPeriod.toDays()).toEqual(61)
    }

    @Test
    fun `should have correct XIRR bounds`() {
      expect(XirrPolicy.XIRR_MIN_BOUND).toEqual(-10.0)
      expect(XirrPolicy.XIRR_MAX_BOUND).toEqual(10.0)
    }
  }
}
