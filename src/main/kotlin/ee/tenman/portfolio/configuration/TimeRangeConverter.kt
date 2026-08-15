package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.domain.TimeRange
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

@Component
class TimeRangeConverter : Converter<String, TimeRange> {
  override fun convert(source: String): TimeRange = TimeRange.from(source)
}
