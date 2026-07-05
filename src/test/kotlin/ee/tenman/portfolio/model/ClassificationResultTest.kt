package ee.tenman.portfolio.model

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.notToThrow
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class ClassificationResultTest {
  @Test
  fun `should fail when every attempted holding failed`() {
    val result = ClassificationResult(success = 0, failure = 3, skipped = 1)

    expect { result.requireAnySuccess("Sector") }
      .toThrow<IllegalStateException>()
      .messageToContain("Sector classification failed for all 3 holdings")
  }

  @Test
  fun `should pass when at least one holding succeeds`() {
    val result = ClassificationResult(success = 1, failure = 7, skipped = 0)

    expect { result.requireAnySuccess("Country") }.notToThrow()
  }

  @Test
  fun `should pass when nothing was attempted`() {
    val result = ClassificationResult(success = 0, failure = 0, skipped = 4)

    expect { result.requireAnySuccess("Country") }.notToThrow()
  }
}
