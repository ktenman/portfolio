package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class Resilience4jConfigurationTest {
  @Test
  fun `cannot retry job execution when job fails with illegal state`() {
    val predicate =
      Resilience4jConfiguration()
        .retryRegistry()
        .retry("job-execution")
        .retryConfig.exceptionPredicate

    expect(predicate.test(IllegalStateException("Sector classification failed for all 3 holdings"))).toEqual(false)
  }

  @Test
  fun `should retry job execution when job fails with generic exception`() {
    val predicate =
      Resilience4jConfiguration()
        .retryRegistry()
        .retry("job-execution")
        .retryConfig.exceptionPredicate

    expect(predicate.test(RuntimeException("connection reset by peer"))).toEqual(true)
  }
}
