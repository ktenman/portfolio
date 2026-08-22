package ee.tenman.portfolio.testing

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import e2e.retry.Retry
import e2e.retry.RetryExtension
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RetryExtension::class)
@Retry(times = 3, onExceptions = [IllegalStateException::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetryExtensionTest {
  private var attempts = 0

  @Test
  fun `should re-invoke the test method until an attempt succeeds`() {
    attempts++
    if (attempts < 3) throw IllegalStateException("attempt $attempts fails on purpose")
    expect(attempts).toEqual(3)
  }

  @AfterAll
  fun `should have re-invoked the method three times`() {
    expect(attempts).toEqual(3)
  }
}
