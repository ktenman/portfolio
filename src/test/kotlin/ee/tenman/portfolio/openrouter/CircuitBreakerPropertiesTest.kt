package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class CircuitBreakerPropertiesTest {
  @Test
  fun `should create properties with default values`() {
    val properties = CircuitBreakerProperties()

    expect(properties.failureThreshold).toEqual(3)
    expect(properties.recoveryTimeoutSeconds).toEqual(60L)
  }

  @Test
  fun `should create properties with custom values`() {
    val properties =
      CircuitBreakerProperties(
        failureThreshold = 5,
        recoveryTimeoutSeconds = 120,
      )

    expect(properties.failureThreshold).toEqual(5)
    expect(properties.recoveryTimeoutSeconds).toEqual(120L)
  }

  @Test
  fun `should throw exception when failureThreshold is zero`() {
    expect {
      CircuitBreakerProperties(failureThreshold = 0)
    }.toThrow<IllegalArgumentException> {
      messageToContain("failureThreshold must be positive")
    }
  }

  @Test
  fun `should throw exception when failureThreshold is negative`() {
    expect {
      CircuitBreakerProperties(failureThreshold = -1)
    }.toThrow<IllegalArgumentException> {
      messageToContain("failureThreshold must be positive")
    }
  }

  @Test
  fun `should throw exception when recoveryTimeoutSeconds is zero`() {
    expect {
      CircuitBreakerProperties(recoveryTimeoutSeconds = 0)
    }.toThrow<IllegalArgumentException> {
      messageToContain("recoveryTimeoutSeconds must be positive")
    }
  }

  @Test
  fun `should throw exception when recoveryTimeoutSeconds is negative`() {
    expect {
      CircuitBreakerProperties(recoveryTimeoutSeconds = -1)
    }.toThrow<IllegalArgumentException> {
      messageToContain("recoveryTimeoutSeconds must be positive")
    }
  }
}
