package ee.tenman.portfolio.configuration

import io.netty.channel.ChannelOption
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration

@Configuration
class WebClientConfig(
  private val properties: WebClientProperties,
) {
  @Bean
  fun directApiWebClient(): WebClient = createWebClient(DIRECT_API_POOL, properties.directApi)

  @Bean
  fun proxyWebClient(): WebClient = createWebClient(PROXY_POOL, properties.proxy)

  @Bean
  fun veegoApiWebClient(): WebClient = createWebClient(VEEGO_POOL, properties.veego)

  private fun createWebClient(
    poolName: String,
    config: WebClientProperties.PoolConfig,
  ): WebClient {
    val builder = WebClient.builder()
    config.url?.let { builder.baseUrl(it) }
    return builder.clientConnector(createConnector(poolName, config)).build()
  }

  private fun createConnector(
    poolName: String,
    config: WebClientProperties.PoolConfig,
  ): ReactorClientHttpConnector {
    val connectionProvider =
      ConnectionProvider
        .builder(poolName)
        .maxConnections(config.maxConnections)
        .maxIdleTime(Duration.ofSeconds(config.maxIdleTimeSeconds))
        .maxLifeTime(Duration.ofMinutes(config.maxLifeTimeMinutes))
        .pendingAcquireTimeout(Duration.ofSeconds(config.pendingAcquireTimeoutSeconds))
        .build()
    val httpClient =
      HttpClient
        .create(connectionProvider)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.connectionTimeoutMs)
        .responseTimeout(Duration.ofSeconds(config.responseTimeoutSeconds))
    return ReactorClientHttpConnector(httpClient)
  }

  companion object {
    private const val DIRECT_API_POOL = "direct-api-pool"
    private const val PROXY_POOL = "proxy-pool"
    private const val VEEGO_POOL = "veego-pool"
  }
}
