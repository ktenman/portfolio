package ee.tenman.portfolio.dto

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigDecimal

class DiversificationDtoTest {
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

  @Test
  fun `should serialize and deserialize DiversificationConfigDto`() {
    val dto =
      DiversificationConfigDto(
        allocations =
          listOf(
            DiversificationConfigAllocationDto(instrumentId = 1L, value = BigDecimal("50.5")),
            DiversificationConfigAllocationDto(instrumentId = 2L, value = BigDecimal("49.5")),
          ),
        inputMode = "percentage",
        actionDisplayMode = "amount",
      )
    val bytes =
      ByteArrayOutputStream().use { baos ->
        ObjectOutputStream(baos).use { it.writeObject(dto) }
        baos.toByteArray()
      }
    val deserialized =
      ObjectInputStream(ByteArrayInputStream(bytes)).use {
        it.readObject() as DiversificationConfigDto
      }
    expect(deserialized.inputMode).toEqual("percentage")
    expect(deserialized.actionDisplayMode).toEqual("amount")
    expect(deserialized.allocations).toHaveSize(2)
    expect(deserialized.allocations[0].instrumentId).toEqual(1L)
    expect(deserialized.allocations[0].value).toEqualNumerically(BigDecimal("50.5"))
    expect(deserialized.allocations[1].instrumentId).toEqual(2L)
    expect(deserialized.allocations[1].value).toEqualNumerically(BigDecimal("49.5"))
  }

  @Test
  fun `should serialize and deserialize DiversificationConfigAllocationDto`() {
    val dto = DiversificationConfigAllocationDto(instrumentId = 42L, value = BigDecimal("123.456"))

    val bytes =
      ByteArrayOutputStream().use { baos ->
      ObjectOutputStream(baos).use { it.writeObject(dto) }
      baos.toByteArray()
    }
    val deserialized =
      ObjectInputStream(ByteArrayInputStream(bytes)).use {
      it.readObject() as DiversificationConfigAllocationDto
    }

    expect(deserialized.instrumentId).toEqual(42L)
    expect(deserialized.value).toEqualNumerically(BigDecimal("123.456"))
  }

  @Test
  fun `should serialize and deserialize empty allocations list`() {
    val dto = DiversificationConfigDto(allocations = emptyList(), inputMode = "amount")

    val bytes =
      ByteArrayOutputStream().use { baos ->
      ObjectOutputStream(baos).use { it.writeObject(dto) }
      baos.toByteArray()
    }
    val deserialized =
      ObjectInputStream(ByteArrayInputStream(bytes)).use {
      it.readObject() as DiversificationConfigDto
    }

    expect(deserialized.inputMode).toEqual("amount")
    expect(deserialized.allocations).toHaveSize(0)
  }
}
