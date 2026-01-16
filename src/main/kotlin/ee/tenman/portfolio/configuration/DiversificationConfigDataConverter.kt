package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.domain.DiversificationConfigData
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class DiversificationConfigDataConverter : AttributeConverter<DiversificationConfigData, String> {
  private val objectMapper = JsonMapperFactory.instance

  override fun convertToDatabaseColumn(attribute: DiversificationConfigData?): String? =
    attribute?.let { objectMapper.writeValueAsString(it) }

  override fun convertToEntityAttribute(dbData: String?): DiversificationConfigData? =
    dbData?.let { objectMapper.readValue(it, DiversificationConfigData::class.java) }
}
