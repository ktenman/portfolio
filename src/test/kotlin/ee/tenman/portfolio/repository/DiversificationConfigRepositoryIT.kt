package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.DiversificationAllocationData
import ee.tenman.portfolio.domain.DiversificationConfig
import ee.tenman.portfolio.domain.DiversificationConfigData
import ee.tenman.portfolio.domain.InputMode
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@IntegrationTest
class DiversificationConfigRepositoryIT {
  @Resource
  private lateinit var repository: DiversificationConfigRepository

  @Test
  fun `should save and retrieve config with JSON data`() {
    val configData =
      DiversificationConfigData(
      allocations =
        listOf(
        DiversificationAllocationData(instrumentId = 1L, value = BigDecimal("50.5")),
        DiversificationAllocationData(instrumentId = 2L, value = BigDecimal("49.5")),
      ),
        inputMode = InputMode.PERCENTAGE,
    )
    val config = DiversificationConfig(configData = configData)

    repository.save(config)
    val result = repository.findConfig()

    expect(result).notToEqualNull()
    expect(result!!.configData.allocations).toHaveSize(2)
    expect(result.configData.allocations[0].instrumentId).toEqual(1L)
    expect(result.configData.allocations[0].value).toEqualNumerically(BigDecimal("50.5"))
    expect(result.configData.allocations[1].instrumentId).toEqual(2L)
    expect(result.configData.allocations[1].value).toEqualNumerically(BigDecimal("49.5"))
    expect(result.configData.inputMode).toEqual(InputMode.PERCENTAGE)
  }

  @Test
  fun `should return null when no config exists`() {
    val result = repository.findConfig()

    expect(result).toEqual(null)
  }

  @Test
  fun `should update existing config`() {
    val initialConfig =
      DiversificationConfig(
      configData =
        DiversificationConfigData(
        allocations = listOf(DiversificationAllocationData(instrumentId = 1L, value = BigDecimal("100"))),
        inputMode = InputMode.PERCENTAGE,
      ),
        )
    repository.save(initialConfig)
    val saved = repository.findConfig()!!
    saved.configData =
      DiversificationConfigData(
      allocations = listOf(DiversificationAllocationData(instrumentId = 2L, value = BigDecimal("200"))),
      inputMode = InputMode.AMOUNT,
    )
    repository.save(saved)

    val result = repository.findConfig()

    expect(result).notToEqualNull()
    expect(result!!.configData.allocations).toHaveSize(1)
    expect(result.configData.allocations[0].instrumentId).toEqual(2L)
    expect(result.configData.allocations[0].value).toEqualNumerically(BigDecimal("200"))
    expect(result.configData.inputMode).toEqual(InputMode.AMOUNT)
  }
}
