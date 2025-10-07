package ee.tenman.portfolio.scheduler

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "ft.adaptive-scheduling")
data class AdaptiveSchedulingProperties(
  var enabled: Boolean = true,
  var minimumIntervalSeconds: Long = 60,
)
