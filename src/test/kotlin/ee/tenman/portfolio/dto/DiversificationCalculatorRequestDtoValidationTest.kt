package ee.tenman.portfolio.dto

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DiversificationCalculatorRequestDtoValidationTest {
  private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

  @Test
  fun `should pass validation for valid request with single allocation`() {
    val request =
      DiversificationCalculatorRequestDto(
        allocations = listOf(AllocationDto(instrumentId = 1L, percentage = BigDecimal("100"))),
      )

    val violations = validator.validate(request)

    expect(violations).toBeEmpty()
  }

  @Test
  fun `should pass validation for valid request with multiple allocations`() {
    val request =
      DiversificationCalculatorRequestDto(
        allocations =
          listOf(
            AllocationDto(instrumentId = 1L, percentage = BigDecimal("50")),
            AllocationDto(instrumentId = 2L, percentage = BigDecimal("30")),
            AllocationDto(instrumentId = 3L, percentage = BigDecimal("20")),
          ),
      )

    val violations = validator.validate(request)

    expect(violations).toBeEmpty()
  }

  @Test
  fun `should pass validation for empty allocations list`() {
    val request = DiversificationCalculatorRequestDto(allocations = emptyList())

    val violations = validator.validate(request)

    expect(violations).toBeEmpty()
  }

  @Test
  fun `should fail validation when allocation has invalid instrument id`() {
    val request =
      DiversificationCalculatorRequestDto(
        allocations = listOf(AllocationDto(instrumentId = -1L, percentage = BigDecimal("100"))),
      )

    val violations = validator.validate(request)

    expect(violations).toHaveSize(1)
  }

  @Test
  fun `should fail validation when allocation has negative percentage`() {
    val request =
      DiversificationCalculatorRequestDto(
        allocations = listOf(AllocationDto(instrumentId = 1L, percentage = BigDecimal("-10"))),
      )

    val violations = validator.validate(request)

    expect(violations).toHaveSize(1)
  }

  @Test
  fun `should fail validation for multiple invalid allocations`() {
    val request =
      DiversificationCalculatorRequestDto(
        allocations =
          listOf(
            AllocationDto(instrumentId = -1L, percentage = BigDecimal("50")),
            AllocationDto(instrumentId = 2L, percentage = BigDecimal("-10")),
          ),
      )

    val violations = validator.validate(request)

    expect(violations).toHaveSize(2)
  }

  @Test
  fun `should validate nested allocations with cascading validation`() {
    val request =
      DiversificationCalculatorRequestDto(
        allocations =
          listOf(
            AllocationDto(instrumentId = 1L, percentage = BigDecimal("50")),
            AllocationDto(instrumentId = 0L, percentage = BigDecimal("-5")),
          ),
      )

    val violations = validator.validate(request)

    expect(violations).toHaveSize(2)
  }
}
