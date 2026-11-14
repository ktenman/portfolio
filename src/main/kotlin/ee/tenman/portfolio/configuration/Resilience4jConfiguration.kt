package ee.tenman.portfolio.configuration

import feign.FeignException
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Duration

@Configuration
@Profile("!test")
class Resilience4jConfiguration {
  @Bean
  fun circuitBreakerRegistry(): CircuitBreakerRegistry {
    val config =
      CircuitBreakerConfig
        .custom()
      .failureRateThreshold(60f)
      .minimumNumberOfCalls(10)
      .slidingWindowSize(20)
      .waitDurationInOpenState(Duration.ofMinutes(5))
      .permittedNumberOfCallsInHalfOpenState(1)
      .slowCallDurationThreshold(Duration.ofHours(2))
      .automaticTransitionFromOpenToHalfOpenEnabled(true)
      .build()

    return CircuitBreakerRegistry.of(config)
  }

  @Bean
  fun retryRegistry(): RetryRegistry {
    val config =
      RetryConfig
        .custom<Any>()
      .maxAttempts(3)
      .waitDuration(Duration.ofSeconds(2))
      .retryExceptions(Exception::class.java)
      .ignoreExceptions(IllegalStateException::class.java, FeignException.FeignClientException::class.java)
      .build()

    return RetryRegistry.of(config)
  }

  @Bean
  fun circuitBreakerMetrics(circuitBreakerRegistry: CircuitBreakerRegistry): MeterBinder =
    TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry)
}
