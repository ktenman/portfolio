package ee.tenman.portfolio.service.infrastructure

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class LiveUpdateServiceTest {
  @Test
  fun `should end a subscription after ten minutes`() {
    expect(LiveUpdateService().subscribe().timeout).toEqual(600_000L)
  }
}
