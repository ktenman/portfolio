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
  fun `should record a persisted symbol as changed by default`() {
    val run = CollectionRun(listOf("Ärikinnisvara"))
    run.persisted("Ärikinnisvara")
    expect(run.result().changed).toEqual(setOf("Ärikinnisvara"))
  }

  @Test
  fun `should not record a symbol persisted without a change as changed`() {
    val run = CollectionRun(listOf("Ärikinnisvara"))
    run.persisted("Ärikinnisvara", changed = false)
    expect(run.result().changed).toEqual(emptySet())
  }

  @Test
  fun `should count a symbol persisted without a change as persisted`() {
    val run = CollectionRun(listOf("Ärikinnisvara"))
    run.persisted("Ärikinnisvara", changed = false)
    expect(run.result().persisted).toEqual(setOf("Ärikinnisvara"))
  }

  @Test
  fun `should record persistence only after durable callback succeeds`() {
    val run = CollectionRun(listOf("A")) { throw IllegalStateException("database unavailable") }
    expect { run.persisted("A") }.toThrow<IllegalStateException>()
    expect(run.result().persisted).toEqual(emptySet())
  }
}
