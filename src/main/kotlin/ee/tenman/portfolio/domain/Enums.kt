package ee.tenman.portfolio.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class TransactionType {
  BUY,
  SELL,
}

enum class InstrumentCategory {
  CASH,
  CRYPTO,
  ETF,
}

enum class JobStatus {
  SUCCESS,
  FAILURE,
  SKIPPED,
}

enum class ProviderName {
  BINANCE,
  FT,
  LIGHTYEAR,
  MANUAL,
  SYNTHETIC,
  TRADING212,
}

enum class BenchmarkIndex {
  SP500,
  WORLD,
}

enum class SectorSource {
  LLM,
  LIGHTYEAR,
}

enum class LogoSource {
  LIGHTYEAR,
  NVSTLY_ICONS,
  BING,
  LLM_SELECTED,
  MANUAL,
}

enum class ActionDisplayMode {
  UNITS,
  AMOUNT,
  ;

  @JsonValue
  fun toJson(): String = name.lowercase()

  companion object {
    @JvmStatic
    @JsonCreator
    fun fromString(value: String): ActionDisplayMode =
      entries.find { it.name.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown action display mode: $value")
  }
}

enum class InputMode {
  PERCENTAGE,
  AMOUNT,
  ;

  @JsonValue
  fun toJson(): String = name.lowercase()

  companion object {
    @JvmStatic
    @JsonCreator
    fun fromString(value: String): InputMode =
      entries.find { it.name.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown input mode: $value")
  }
}

enum class Currency {
  EUR,
  USD,
  GBP,
  CHF,
  JPY,
  CAD,
  AUD,
  SEK,
  NOK,
  DKK,
  HKD,
  SGD,
  ;

  companion object {
    fun fromCodeOrNull(code: String?): Currency? = code?.let { runCatching { valueOf(it.uppercase()) }.getOrNull() }
  }
}

enum class Platform(
  val displayName: String,
) {
  AVIVA("Aviva"),
  BINANCE("Binance"),
  COINBASE("Coinbase"),
  IBKR("IBKR"),
  LHV("LHV"),
  LIGHTYEAR("Lightyear"),
  LIGHTYEAR_BUSINESS("Lightyear Business"),
  SWEDBANK("Swedbank"),
  TRADING212("Trading 212"),
  UNKNOWN("Unknown"),
  ;

  companion object {
    fun fromStringOrNull(value: String): Platform? = runCatching { valueOf(value.uppercase()) }.getOrNull()

    fun parseList(values: List<String>?): List<Platform>? {
      if (values.isNullOrEmpty()) return null
      val parsed = values.mapNotNull { fromStringOrNull(it) }.sortedBy { it.name }
      return parsed.ifEmpty { null }
    }
  }
}

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

enum class VisionModel(
  val modelId: String,
  val isOpenRouter: Boolean = true,
) {
  LLAMA_4_SCOUT("meta-llama/llama-4-scout"),
  NOVA_LITE("amazon/nova-lite-v1"),
  GOOGLE_VISION("google-vision", isOpenRouter = false),
}

data class PlatformDto(
  val name: String,
  val displayName: String,
)

data class EnumsResponse(
  val platforms: List<PlatformDto>,
  val providers: List<String>,
  val transactionTypes: List<String>,
  val categories: List<String>,
  val currencies: List<String>,
)
