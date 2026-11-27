package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import java.io.Serializable
import java.time.LocalDateTime
import java.util.Collections

class JdkSerializationHintsTest {
  @Test
  fun `should register linked hash maps for serialization`() {
    expect(registered(LinkedHashMap::class.java)).toEqual(true)
  }

  @Test
  fun `should register boxed longs for serialization`() {
    expect(registered(Long::class.javaObjectType)).toEqual(true)
  }

  @Test
  fun `should register local date times for serialization`() {
    expect(registered(LocalDateTime::class.java)).toEqual(true)
  }

  @Test
  fun `should register unmodifiable list views for serialization`() {
    expect(registered(Collections.unmodifiableList(arrayListOf("Ödü"))::class.java)).toEqual(true)
  }

  @Test
  fun `should not register the serializable interface itself`() {
    expect(registered(Serializable::class.java)).toEqual(false)
  }

  private fun registered(type: Class<*>): Boolean {
    val hints = RuntimeHints().also { JdkSerializationHints().registerHints(it, null) }
    return RuntimeHintsPredicates.serialization().onType(type).test(hints)
  }
}
