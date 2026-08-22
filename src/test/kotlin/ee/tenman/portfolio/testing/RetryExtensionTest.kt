package ee.tenman.portfolio.testing

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import e2e.retry.Retry
import e2e.retry.RetryExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RetryExtension::class)
@Retry(times = 3, onExceptions = [IllegalStateException::class])
class RetryExtensionTest {
  private var setups = 0
  private var attempts = 0

  @BeforeEach
  fun setUp() {
    setups++
  }

  @Test
  fun `should re-run the per-test lifecycle before every retried attempt`() {
    attempts++
    if (attempts < 3) throw IllegalStateException("attempt $attempts fails on purpose")
    expect(setups).toEqual(attempts)
  }
}
