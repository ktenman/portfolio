package ee.tenman.portfolio.domain

enum class IndustrySector(
  val displayName: String,
) {
  SEMICONDUCTORS("Semiconductors"),
  DIGITAL_HARDWARE("Digital Hardware"),
  SOFTWARE_CLOUD_SERVICES("Software & Cloud Services"),
  COMMUNICATION("Communication"),
  BUSINESS_SERVICES("Business Services"),
  CONSUMER_ESSENTIALS("Consumer Essentials"),
  HEALTH("Health"),
  INDUSTRIALS("Industrials"),
  MOBILITY("Mobility"),
  CONSUMER_DISCRETIONARY("Consumer Discretionary"),
  ENERGY("Energy"),
  UTILITIES("Utilities"),
  FINANCE("Finance"),
  CRYPTOCURRENCY("Cryptocurrency"),
  ;

  companion object {
    fun fromDisplayName(displayName: String): IndustrySector? = entries.find { it.displayName.equals(displayName, ignoreCase = true) }

    fun getAllDisplayNames(): String = entries.joinToString(", ") { it.displayName }
  }
}
