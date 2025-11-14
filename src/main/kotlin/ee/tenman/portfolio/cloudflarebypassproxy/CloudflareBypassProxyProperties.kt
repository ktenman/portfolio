package ee.tenman.portfolio.cloudflarebypassproxy

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "cloudflare-bypass-proxy")
data class CloudflareBypassProxyProperties(
  var url: String = "http://localhost:3000",
)
