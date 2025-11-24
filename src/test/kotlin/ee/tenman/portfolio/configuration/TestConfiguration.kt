package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.job.BinanceDataRetrievalJob
import ee.tenman.portfolio.job.FtDataRetrievalJob
import ee.tenman.portfolio.job.LightyearHistoricalDataRetrievalJob
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.time.Duration

@TestConfiguration
@Profile("test")
class TestConfiguration {
  @Bean
  @Primary
  fun binanceDataRetrievalJob(): BinanceDataRetrievalJob = mockk(relaxed = true)

  @Bean
  @Primary
  fun ftDataRetrievalJob(): FtDataRetrievalJob = mockk(relaxed = true)

  @Bean
  @Primary
  fun lightyearHistoricalDataRetrievalJob(): LightyearHistoricalDataRetrievalJob = mockk(relaxed = true)

  @Bean
  @Primary
  fun circuitBreakerRegistry(): CircuitBreakerRegistry {
    val config =
      CircuitBreakerConfig
        .custom()
        .failureRateThreshold(60f)
        .minimumNumberOfCalls(10)
        .slidingWindowSize(20)
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .permittedNumberOfCallsInHalfOpenState(5)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .build()

    return CircuitBreakerRegistry.of(config)
  }

  @Bean
  @Primary
  fun retryRegistry(): RetryRegistry {
    val config =
      RetryConfig
        .custom<Any>()
        .maxAttempts(3)
        .waitDuration(Duration.ofSeconds(2))
        .retryExceptions(Exception::class.java)
        .build()

    return RetryRegistry.of(config)
  }
}
