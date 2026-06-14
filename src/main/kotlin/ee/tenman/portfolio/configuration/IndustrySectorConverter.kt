package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.domain.IndustrySector
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class IndustrySectorConverter : AttributeConverter<IndustrySector?, String?> {
  override fun convertToDatabaseColumn(attribute: IndustrySector?): String? = attribute?.displayName

  override fun convertToEntityAttribute(dbData: String?): IndustrySector? = dbData?.let { IndustrySector.fromDisplayName(it) }
}
