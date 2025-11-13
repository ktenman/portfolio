package ee.tenman.portfolio.trading212

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "trading212")
data class Trading212Properties(
  var proxy: ProxyConfig = ProxyConfig(),
  var batchSize: Int = 15,
) {
  data class ProxyConfig(
    var url: String = "http://localhost:3000",
  )
}
