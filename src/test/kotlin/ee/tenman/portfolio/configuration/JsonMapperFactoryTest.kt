package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toEndWith
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toStartWith
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class JsonMapperFactoryTest {
  @Test
  fun `should return the full json when the serialized value fits within the limit`() {
    expect(JsonMapperFactory.truncatedJson(mapOf("naam" to "Õäöü"))).toEqual("{\"naam\":\"Õäöü\"}")
  }

  @Test
  fun `should append an ellipsis marker when the serialized value exceeds the limit`() {
    expect(JsonMapperFactory.truncatedJson((1..1000).toList())).toEndWith(" ...")
  }

  @Test
  fun `should keep the head of the json when truncating an oversized value`() {
    expect(JsonMapperFactory.truncatedJson((1..1000).toList())).toStartWith("[1,2,3,")
  }
}
