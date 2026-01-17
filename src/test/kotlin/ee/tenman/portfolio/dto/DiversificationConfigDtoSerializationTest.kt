package ee.tenman.portfolio.dto

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigDecimal

class DiversificationConfigDtoSerializationTest {
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
