package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.domain.ActionDisplayMode
import ee.tenman.portfolio.domain.DiversificationAllocationData
import ee.tenman.portfolio.domain.DiversificationConfigData
import ee.tenman.portfolio.domain.InputMode
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer

class DiversificationConfigDataDeserializer : ValueDeserializer<DiversificationConfigData>() {
  override fun deserialize(
    parser: JsonParser,
    ctx: DeserializationContext,
  ): DiversificationConfigData {
    val node: JsonNode = ctx.readTree(parser)
    val allocations = readAllocations(ctx, node)
    val inputMode = readInputMode(node)
    val selectedPlatforms = readSelectedPlatforms(node)
    val optimizeEnabled = node.get("optimizeEnabled")?.asBoolean() ?: false
    val totalInvestment = node.get("totalInvestment")?.asDouble() ?: 0.0
    val actionDisplayMode = readActionDisplayMode(node)
    val buyOnlyEnabled = node.get("buyOnlyEnabled")?.asBoolean() ?: false
    return DiversificationConfigData(
      allocations,
      inputMode,
      selectedPlatforms,
      optimizeEnabled,
      totalInvestment,
      actionDisplayMode,
      buyOnlyEnabled,
    )
  }

  private fun readAllocations(
    ctx: DeserializationContext,
    node: JsonNode,
  ): List<DiversificationAllocationData> {
    val allocationsNode = node.get("allocations") ?: return emptyList()
    val type =
      ctx.typeFactory.constructCollectionType(
        List::class.java,
        DiversificationAllocationData::class.java,
      )
    return ctx.readTreeAsValue(allocationsNode, type)
  }

  private fun readInputMode(node: JsonNode): InputMode =
    node.get("inputMode")?.asString()?.let { InputMode.fromString(it) } ?: InputMode.PERCENTAGE

  private fun readSelectedPlatforms(node: JsonNode): List<String> {
    val listNode = node.get("selectedPlatforms")
    if (listNode?.isArray == true) return listNode.values().map { it.asString() }
    val legacy = node.get("selectedPlatform")
    val value = if (legacy == null || legacy.isNull) "" else legacy.asString()
    return if (value.isNotBlank()) listOf(value) else emptyList()
  }

  private fun readActionDisplayMode(node: JsonNode): ActionDisplayMode =
    node.get("actionDisplayMode")?.asString()?.let { ActionDisplayMode.fromString(it) }
      ?: ActionDisplayMode.UNITS
}
