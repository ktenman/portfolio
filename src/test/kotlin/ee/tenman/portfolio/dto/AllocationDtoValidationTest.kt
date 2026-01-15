package ee.tenman.portfolio.dto

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AllocationDtoValidationTest {
  private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

  @Test
  fun `should pass validation for valid allocation`() {
    val allocation = AllocationDto(instrumentId = 1L, percentage = BigDecimal("50.0"))

    val violations = validator.validate(allocation)

    expect(violations).toBeEmpty()
  }

  @Test
  fun `should pass validation for zero percentage`() {
    val allocation = AllocationDto(instrumentId = 1L, percentage = BigDecimal.ZERO)

    val violations = validator.validate(allocation)

    expect(violations).toBeEmpty()
  }

  @Test
  fun `should pass validation for 100 percent allocation`() {
    val allocation = AllocationDto(instrumentId = 1L, percentage = BigDecimal("100"))

    val violations = validator.validate(allocation)

    expect(violations).toBeEmpty()
  }

  @Test
  fun `should fail validation for zero instrument id`() {
    val allocation = AllocationDto(instrumentId = 0L, percentage = BigDecimal("50.0"))

    val violations = validator.validate(allocation)

    expect(violations).toHaveSize(1)
    expect(violations.first().message).toContain("must be positive")
  }

  @Test
  fun `should fail validation for negative instrument id`() {
    val allocation = AllocationDto(instrumentId = -1L, percentage = BigDecimal("50.0"))

    val violations = validator.validate(allocation)

    expect(violations).toHaveSize(1)
    expect(violations.first().message).toContain("must be positive")
  }

  @Test
  fun `should fail validation for negative percentage`() {
    val allocation = AllocationDto(instrumentId = 1L, percentage = BigDecimal("-10.0"))

    val violations = validator.validate(allocation)

    expect(violations).toHaveSize(1)
    expect(violations.first().message).toContain("non-negative")
  }

  @Test
  fun `should fail validation for both invalid instrument id and percentage`() {
    val allocation = AllocationDto(instrumentId = -1L, percentage = BigDecimal("-10.0"))

    val violations = validator.validate(allocation)

    expect(violations).toHaveSize(2)
  }
}
