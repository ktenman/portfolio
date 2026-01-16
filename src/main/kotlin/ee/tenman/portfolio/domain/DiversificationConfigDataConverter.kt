package ee.tenman.portfolio.domain

import ee.tenman.portfolio.configuration.JsonMapperFactory
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = false)
class DiversificationConfigDataConverter : AttributeConverter<DiversificationConfigData, String> {
  private val objectMapper = JsonMapperFactory.instance

  override fun convertToDatabaseColumn(attribute: DiversificationConfigData?): String? =
    attribute?.let { objectMapper.writeValueAsString(it) }

  override fun convertToEntityAttribute(dbData: String?): DiversificationConfigData? =
    dbData?.let { objectMapper.readValue(it, DiversificationConfigData::class.java) }
}
