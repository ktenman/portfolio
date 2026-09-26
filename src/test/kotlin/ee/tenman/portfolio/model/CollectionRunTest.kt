package ee.tenman.portfolio.model

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class CollectionRunTest {
  @Test
  fun `should count each expected persisted symbol once`() {
    val run = CollectionRun(listOf("A", "B"))
    run.attempted("A")
    run.fetched("A")
    run.persisted("A")
    run.persisted("A")
    run.persisted("unknown")
    expect(run.result().persisted).toEqual(setOf("A"))
  }

  @Test
  fun `should report missing expected symbols as failed`() {
    val run = CollectionRun(listOf("A", "B"))
    run.attempted("A")
    run.fetched("A")
    run.persisted("A")
    expect(run.result().failed).toEqual(setOf("B"))
  }

  @Test
  fun `should record persistence only after durable callback succeeds`() {
    val run = CollectionRun(listOf("A")) { throw IllegalStateException("database unavailable") }
    expect { run.persisted("A") }.toThrow<IllegalStateException>()
    expect(run.result().persisted).toEqual(emptySet())
  }
}
