package ee.tenman.portfolio.configuration

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Duration

@TestConfiguration
class TestConfiguration {
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
