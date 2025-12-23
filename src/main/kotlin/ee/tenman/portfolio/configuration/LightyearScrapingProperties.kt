package ee.tenman.portfolio.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "scraping.lightyear")
data class LightyearScrapingProperties(
  var etfs: List<EtfConfig> = emptyList(),
) {
  data class EtfConfig(
    var symbol: String = "",
  )
}
