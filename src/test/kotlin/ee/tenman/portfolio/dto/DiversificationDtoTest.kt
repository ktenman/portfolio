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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigDecimal

class DiversificationDtoTest {
  @ParameterizedTest
  @ValueSource(strings = ["50.0", "0", "100"])
  fun `should accept an allocation with a positive id and a non-negative percentage`(percentage: BigDecimal) {
    val violations = validator.validate(AllocationDto(instrumentId = 1L, percentage = percentage))

    expect(violations).toBeEmpty()
  }

  @ParameterizedTest
  @CsvSource("0, 50.0, must be positive", "-1, 50.0, must be positive", "1, -10.0, non-negative")
  fun `should reject an allocation with a non-positive id or a negative percentage`(
    instrumentId: Long,
    percentage: BigDecimal,
    message: String,
  ) {
    val violations = validator.validate(AllocationDto(instrumentId, percentage))

    expect(violations).toHaveSize(1)
    expect(violations.first().message).toContain(message)
  }

  @ParameterizedTest
  @ValueSource(ints = [0, 1, 3])
  fun `should accept a request whose allocations are all valid`(size: Int) {
    val request = DiversificationCalculatorRequestDto((1..size).map { AllocationDto(it.toLong(), BigDecimal.TEN) })

    val violations = validator.validate(request)

    expect(violations).toBeEmpty()
  }

  @Test
  fun `should cascade validation into every nested allocation`() {
    val request =
      DiversificationCalculatorRequestDto(
        listOf(
          AllocationDto(instrumentId = 1L, percentage = BigDecimal("50")),
          AllocationDto(instrumentId = 0L, percentage = BigDecimal("-5")),
          AllocationDto(instrumentId = -1L, percentage = BigDecimal("20")),
        ),
      )

    val violations = validator.validate(request)

    expect(violations).toHaveSize(3)
  }

  @Test
  fun `should survive a redis serialization round trip with its allocations intact`() {
    val dto =
      DiversificationConfigDto(
        allocations = listOf(DiversificationConfigAllocationDto(instrumentId = 42L, value = BigDecimal("50.5"))),
        inputMode = "percentage",
        actionDisplayMode = "amount",
      )

    val restored = roundTrip(dto)

    expect(restored.inputMode).toEqual("percentage")
    expect(restored.actionDisplayMode).toEqual("amount")
    expect(restored.allocations).toHaveSize(1)
    expect(restored.allocations.first().instrumentId).toEqual(42L)
    expect(restored.allocations.first().value).toEqualNumerically(BigDecimal("50.5"))
  }

  private fun <T> roundTrip(value: T): T {
    val bytes =
      ByteArrayOutputStream().use { out ->
        ObjectOutputStream(out).use { it.writeObject(value) }
        out.toByteArray()
      }
    @Suppress("UNCHECKED_CAST")
    return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as T }
  }

  companion object {
    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator
  }
}
