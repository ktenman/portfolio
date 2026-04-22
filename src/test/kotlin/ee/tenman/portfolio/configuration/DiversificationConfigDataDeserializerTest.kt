package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.ActionDisplayMode
import ee.tenman.portfolio.domain.DiversificationConfigData
import ee.tenman.portfolio.domain.InputMode
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class DiversificationConfigDataDeserializerTest {
  private val mapper: JsonMapper = JsonMapperFactory.instance

  @Test
  fun `should deserialize legacy single selectedPlatform into selectedPlatforms list`() {
    val json =
      """
      {
        "allocations": [],
        "inputMode": "PERCENTAGE",
        "selectedPlatform": "LHV",
        "optimizeEnabled": false,
        "totalInvestment": 0.0,
        "actionDisplayMode": "UNITS"
      }
    """.trimIndent()

    val result = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(result.selectedPlatforms).toContainExactly("LHV")
  }

  @Test
  fun `should deserialize legacy null selectedPlatform into empty list`() {
    val json =
      """
      {
        "allocations": [],
        "inputMode": "PERCENTAGE",
        "selectedPlatform": null,
        "optimizeEnabled": false,
        "totalInvestment": 0.0,
        "actionDisplayMode": "UNITS"
      }
    """.trimIndent()

    val result = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(result.selectedPlatforms).toBeEmpty()
  }

  @Test
  fun `should deserialize new selectedPlatforms array with multiple values`() {
    val json =
      """
      {
        "allocations": [],
        "inputMode": "PERCENTAGE",
        "selectedPlatforms": ["LHV", "SWEDBANK"],
        "optimizeEnabled": false,
        "totalInvestment": 0.0,
        "actionDisplayMode": "UNITS"
      }
    """.trimIndent()

    val result = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(result.selectedPlatforms).toContainExactly("LHV", "SWEDBANK")
  }

  @Test
  fun `should prefer selectedPlatforms when both fields present`() {
    val json =
      """
      {
        "allocations": [],
        "inputMode": "PERCENTAGE",
        "selectedPlatform": "OLD",
        "selectedPlatforms": ["NEW"],
        "optimizeEnabled": false,
        "totalInvestment": 0.0,
        "actionDisplayMode": "UNITS"
      }
    """.trimIndent()

    val result = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(result.selectedPlatforms).toContainExactly("NEW")
  }

  @Test
  fun `should default selectedPlatforms to empty list when neither field present`() {
    val json =
      """
      {
        "allocations": [],
        "inputMode": "PERCENTAGE",
        "optimizeEnabled": false,
        "totalInvestment": 0.0,
        "actionDisplayMode": "UNITS"
      }
    """.trimIndent()

    val result = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(result.selectedPlatforms).toBeEmpty()
  }

  @Test
  fun `should round-trip modern config through serialize and deserialize`() {
    val original =
      DiversificationConfigData(
        allocations = emptyList(),
        inputMode = InputMode.PERCENTAGE,
        selectedPlatforms = listOf("LHV", "SWEDBANK"),
        optimizeEnabled = true,
        totalInvestment = 1000.0,
        actionDisplayMode = ActionDisplayMode.AMOUNT,
      )

    val json = mapper.writeValueAsString(original)
    val restored = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(restored.selectedPlatforms).toContainExactly("LHV", "SWEDBANK")
    expect(restored.optimizeEnabled).toEqual(true)
    expect(restored.totalInvestment).toEqual(1000.0)
  }

  @Test
  fun `should deserialize buyOnlyEnabled when present`() {
    val json =
      """
      {
        "allocations": [],
        "inputMode": "PERCENTAGE",
        "selectedPlatforms": [],
        "optimizeEnabled": false,
        "totalInvestment": 0.0,
        "actionDisplayMode": "UNITS",
        "buyOnlyEnabled": true
      }
    """.trimIndent()

    val result = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(result.buyOnlyEnabled).toEqual(true)
  }

  @Test
  fun `should default buyOnlyEnabled to false when missing`() {
    val json =
      """
      {
        "allocations": [],
        "inputMode": "PERCENTAGE",
        "selectedPlatforms": [],
        "optimizeEnabled": false,
        "totalInvestment": 0.0,
        "actionDisplayMode": "UNITS"
      }
    """.trimIndent()

    val result = mapper.readValue(json, DiversificationConfigData::class.java)

    expect(result.buyOnlyEnabled).toEqual(false)
  }
}
