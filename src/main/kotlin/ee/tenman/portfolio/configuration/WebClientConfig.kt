package ee.tenman.portfolio.configuration

import io.netty.channel.ChannelOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration

@Configuration
class WebClientConfig {
    @Bean
    fun directApiWebClient(): WebClient =
        WebClient
            .builder()
            .clientConnector(
                createConnector(
                    connectionTimeoutMs = 3000,
                    responseTimeoutSeconds = 10,
                    maxConnections = 50,
                ),
            ).build()

    @Bean
    fun proxyWebClient(
      @Value("\${cloudflare-bypass-proxy.url:http://localhost:3000}") proxyUrl: String,
    ): WebClient =
        WebClient
            .builder()
            .baseUrl(proxyUrl)
            .clientConnector(
                createConnector(
                    connectionTimeoutMs = 5000,
                    responseTimeoutSeconds = 30,
                    maxConnections = 20,
                ),
            ).build()

    private fun createConnector(
      connectionTimeoutMs: Int,
      responseTimeoutSeconds: Long,
      maxConnections: Int,
    ): ReactorClientHttpConnector {
        val connectionProvider =
          ConnectionProvider
            .builder("webclient-pool")
            .maxConnections(maxConnections)
            .maxIdleTime(Duration.ofSeconds(30))
            .maxLifeTime(Duration.ofMinutes(5))
            .pendingAcquireTimeout(Duration.ofSeconds(10))
            .build()
        val httpClient =
          HttpClient
            .create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMs)
            .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
        return ReactorClientHttpConnector(httpClient)
    }
}
