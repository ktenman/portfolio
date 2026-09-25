package ee.tenman.portfolio.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "monitoring.collection")
class CollectionMonitoringProperties {
  var startupGrace: Duration = Duration.ofMinutes(2)
  var priceGrace: Duration = Duration.ofMinutes(2)
  var dailyGrace: Duration = Duration.ofMinutes(30)
}
