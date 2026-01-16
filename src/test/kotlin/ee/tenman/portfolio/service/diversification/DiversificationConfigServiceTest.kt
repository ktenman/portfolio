package ee.tenman.portfolio.service.diversification

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeAnInstanceOf
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DiversificationAllocationData
import ee.tenman.portfolio.domain.DiversificationConfig
import ee.tenman.portfolio.domain.DiversificationConfigData
import ee.tenman.portfolio.domain.InputMode
import ee.tenman.portfolio.dto.DiversificationConfigAllocationDto
import ee.tenman.portfolio.dto.DiversificationConfigDto
import ee.tenman.portfolio.repository.DiversificationConfigRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DiversificationConfigServiceTest {
  private val repository = mockk<DiversificationConfigRepository>()
  private lateinit var service: DiversificationConfigService

  @BeforeEach
  fun setup() {
    service = DiversificationConfigService(repository)
  }

  @Test
  fun `should return null when config does not exist`() {
    every { repository.findConfig() } returns null

    val result = service.getConfig()

    expect(result).toEqual(null)
  }

  @Test
  fun `should return config when it exists`() {
    val configData =
      DiversificationConfigData(
      allocations = listOf(DiversificationAllocationData(instrumentId = 1L, value = BigDecimal("50.5"))),
      inputMode = InputMode.PERCENTAGE,
    )
    val config = DiversificationConfig(configData = configData).apply { id = 1L }
    every { repository.findConfig() } returns config

    val result = service.getConfig()

    expect(result).notToEqualNull()
    expect(result!!.allocations).toHaveSize(1)
    expect(result.allocations[0].instrumentId).toEqual(1L)
    expect(result.allocations[0].value).toEqualNumerically(BigDecimal("50.5"))
    expect(result.inputMode).toEqual("percentage")
  }

  @Test
  fun `should create new config when none exists`() {
    val dto =
      DiversificationConfigDto(
      allocations = listOf(DiversificationConfigAllocationDto(instrumentId = 2L, value = BigDecimal("75.0"))),
      inputMode = "amount",
    )
    val configSlot = slot<DiversificationConfig>()
    every { repository.findConfig() } returns null
    every { repository.save(capture(configSlot)) } answers { configSlot.captured.apply { id = 1L } }

    val result = service.saveConfig(dto)

    expect(result.allocations).toHaveSize(1)
    expect(result.allocations[0].instrumentId).toEqual(2L)
    expect(result.allocations[0].value).toEqualNumerically(BigDecimal("75.0"))
    expect(result.inputMode).toEqual("amount")
    verify(exactly = 1) { repository.save(any()) }
  }

  @Test
  fun `should update existing config`() {
    val existingConfigData =
      DiversificationConfigData(
      allocations = listOf(DiversificationAllocationData(instrumentId = 1L, value = BigDecimal("25.0"))),
      inputMode = InputMode.PERCENTAGE,
    )
    val existingConfig = DiversificationConfig(configData = existingConfigData).apply { id = 1L }
    val dto =
      DiversificationConfigDto(
      allocations = listOf(DiversificationConfigAllocationDto(instrumentId = 3L, value = BigDecimal("100.0"))),
      inputMode = "amount",
    )
    val configSlot = slot<DiversificationConfig>()
    every { repository.findConfig() } returns existingConfig
    every { repository.save(capture(configSlot)) } answers { configSlot.captured }

    val result = service.saveConfig(dto)

    expect(result.allocations).toHaveSize(1)
    expect(result.allocations[0].instrumentId).toEqual(3L)
    expect(result.allocations[0].value).toEqualNumerically(BigDecimal("100.0"))
    expect(result.inputMode).toEqual("amount")
    expect(configSlot.captured.id).toEqual(1L)
    verify(exactly = 1) { repository.save(any()) }
  }

  @Test
  fun `should handle multiple allocations`() {
    val dto =
      DiversificationConfigDto(
      allocations =
        listOf(
        DiversificationConfigAllocationDto(instrumentId = 1L, value = BigDecimal("30.0")),
        DiversificationConfigAllocationDto(instrumentId = 2L, value = BigDecimal("70.0")),
      ),
        inputMode = "percentage",
    )
    val configSlot = slot<DiversificationConfig>()
    every { repository.findConfig() } returns null
    every { repository.save(capture(configSlot)) } answers { configSlot.captured.apply { id = 1L } }

    val result = service.saveConfig(dto)

    expect(result.allocations).toHaveSize(2)
    expect(result.allocations[0].instrumentId).toEqual(1L)
    expect(result.allocations[0].value).toEqualNumerically(BigDecimal("30.0"))
    expect(result.allocations[1].instrumentId).toEqual(2L)
    expect(result.allocations[1].value).toEqualNumerically(BigDecimal("70.0"))
  }

  @Test
  fun `should preserve config data structure when saving`() {
    val dto =
      DiversificationConfigDto(
      allocations = listOf(DiversificationConfigAllocationDto(instrumentId = 5L, value = BigDecimal("50.5"))),
      inputMode = "percentage",
    )
    val configSlot = slot<DiversificationConfig>()
    every { repository.findConfig() } returns null
    every { repository.save(capture(configSlot)) } answers { configSlot.captured.apply { id = 1L } }

    service.saveConfig(dto)

    expect(configSlot.captured.configData).toBeAnInstanceOf<DiversificationConfigData>()
    expect(configSlot.captured.configData.allocations).toHaveSize(1)
    expect(
      configSlot.captured.configData.allocations[0]
      .instrumentId,
        ).toEqual(5L)
    expect(
      configSlot.captured.configData.allocations[0]
      .value,
        ).toEqualNumerically(BigDecimal("50.5"))
    expect(configSlot.captured.configData.inputMode).toEqual(InputMode.PERCENTAGE)
  }
}
