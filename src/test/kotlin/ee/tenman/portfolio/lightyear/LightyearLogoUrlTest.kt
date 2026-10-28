package ee.tenman.portfolio.lightyear

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import feign.FeignException
import feign.Request
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional

class LightyearLogoUrlTest : LightyearPriceServiceTestBase() {
  @Test
  fun `maps each symbol to the logo of its Lightyear instrument`() {
    stubConfiguredUuids()
    every { lightyearPriceClient.getInstrumentBatch(listOf("uuid-vwce", "uuid-sxr8")) } returns
      listOf(response("uuid-vwce", "https://lightyear.test/vwce.png"), response("uuid-sxr8", "https://lightyear.test/sxr8.png"))

    val logos = service.fetchLogoUrls(listOf("VWCE:GER:EUR", "SXR8:GER:EUR"))

    expect(logos).toEqual(
      mapOf("VWCE:GER:EUR" to "https://lightyear.test/vwce.png", "SXR8:GER:EUR" to "https://lightyear.test/sxr8.png"),
    )
  }

  @Test
  fun `leaves out a symbol without a Lightyear id`() {
    stubConfiguredUuids()
    every { properties.findUuidBySymbol("ÕUNAD:TLN:EUR") } returns null
    every { instrumentRepository.findBySymbol("ÕUNAD:TLN:EUR") } returns Optional.empty()
    every { lightyearPriceClient.lookupUuid(any()) } throws RuntimeException("Not found")
    every { lightyearPriceClient.getInstrumentBatch(listOf("uuid-vwce")) } returns
      listOf(response("uuid-vwce", "https://lightyear.test/vwce.png"))

    val logos = service.fetchLogoUrls(listOf("VWCE:GER:EUR", "ÕUNAD:TLN:EUR"))

    expect(logos).toEqual(mapOf("VWCE:GER:EUR" to "https://lightyear.test/vwce.png"))
  }

  @Test
  fun `leaves out an instrument Lightyear has no logo for`() {
    stubConfiguredUuids()
    every { lightyearPriceClient.getInstrumentBatch(listOf("uuid-vwce")) } returns listOf(response("uuid-vwce", null))

    val logos = service.fetchLogoUrls(listOf("VWCE:GER:EUR"))

    expect(logos).toEqual(emptyMap())
  }

  @Test
  fun `returns no logos when Lightyear rejects the batch`() {
    stubConfiguredUuids()
    every { lightyearPriceClient.getInstrumentBatch(any()) } throws badRequest()

    val logos = service.fetchLogoUrls(listOf("VWCE:GER:EUR", "SXR8:GER:EUR"))

    expect(logos).toEqual(emptyMap())
  }

  @Test
  fun `dont call Lightyear without symbols`() {
    service.fetchLogoUrls(emptyList())

    verify(exactly = 0) { lightyearPriceClient.getInstrumentBatch(any()) }
  }

  private fun stubConfiguredUuids() {
    every { properties.findUuidBySymbol("VWCE:GER:EUR") } returns "uuid-vwce"
    every { properties.findUuidBySymbol("SXR8:GER:EUR") } returns "uuid-sxr8"
  }

  private fun response(
    id: String,
    logo: String?,
  ) = LightyearInstrumentResponse(id = id, symbol = id, name = id, exchange = null, logo = logo)

  private fun badRequest(): FeignException =
    FeignException.BadRequest(
      "Bad request",
      Request.create(Request.HttpMethod.POST, "/lightyear/batch", emptyMap(), null, Charsets.UTF_8, null),
      null,
      emptyMap(),
    )
}
