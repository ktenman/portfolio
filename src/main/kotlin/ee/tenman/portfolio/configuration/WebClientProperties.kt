package ee.tenman.portfolio.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "webclient")
data class WebClientProperties(
  val directApi: PoolConfig = PoolConfig(),
  val proxy: PoolConfig =
    PoolConfig(
    url = "http://localhost:3000",
    connectionTimeoutMs = 5000,
    responseTimeoutSeconds = 30,
    maxConnections = 20,
  ),
    val veego: PoolConfig =
      PoolConfig(
    url = "https://api.veego.ee/api",
  ),
      ) {
  data class PoolConfig(
    val url: String? = null,
    val connectionTimeoutMs: Int = 3000,
    val responseTimeoutSeconds: Long = 10,
    val maxConnections: Int = 50,
    val maxIdleTimeSeconds: Long = 30,
    val maxLifeTimeMinutes: Long = 5,
    val pendingAcquireTimeoutSeconds: Long = 10,
  )
}
