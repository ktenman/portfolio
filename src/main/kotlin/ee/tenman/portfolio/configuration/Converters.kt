package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.domain.DiversificationConfigData
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.domain.PoffCatalogue
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.slf4j.LoggerFactory

@Converter(autoApply = true)
class DiversificationConfigDataConverter : AttributeConverter<DiversificationConfigData, String> {
  private val objectMapper = JsonMapperFactory.instance

  override fun convertToDatabaseColumn(attribute: DiversificationConfigData?): String? =
    attribute?.let { objectMapper.writeValueAsString(it) }

  override fun convertToEntityAttribute(dbData: String?): DiversificationConfigData? =
    dbData?.let { objectMapper.readValue(it, DiversificationConfigData::class.java) }
}

@Converter(autoApply = true)
class IndustrySectorConverter : AttributeConverter<IndustrySector?, String?> {
  override fun convertToDatabaseColumn(attribute: IndustrySector?): String? = attribute?.displayName

  override fun convertToEntityAttribute(dbData: String?): IndustrySector? = dbData?.let { IndustrySector.fromDisplayName(it) }
}

@Converter(autoApply = true)
class PoffCatalogueConverter : AttributeConverter<PoffCatalogue, String> {
  private val objectMapper = JsonMapperFactory.instance
  private val log = LoggerFactory.getLogger(javaClass)

  override fun convertToDatabaseColumn(attribute: PoffCatalogue?): String? = attribute?.let { objectMapper.writeValueAsString(it) }

  override fun convertToEntityAttribute(dbData: String?): PoffCatalogue? =
    dbData?.let { json ->
      runCatching { objectMapper.readValue(json, PoffCatalogue::class.java) }
        .onFailure { log.warn("Stored PÖFF catalogue cannot be read, so it counts as not imported", it) }
        .getOrNull()
    }
}
