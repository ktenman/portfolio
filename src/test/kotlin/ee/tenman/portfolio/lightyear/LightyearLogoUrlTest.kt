package ee.tenman.portfolio.lightyear

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
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
  fun `looks up an ETF another provider prices by its ticker instead of sending its foreign id`() {
    val etf =
      Instrument(
        symbol = "BNKE:PAR:EUR",
        name = "Amundi Euro Stoxx Banks",
        category = "ETF",
        baseCurrency = "EUR",
        providerName = ProviderName.TRADING212,
        providerExternalId = "BNKEp_EQ",
      )
    every { properties.findUuidBySymbol("BNKE:PAR:EUR") } returns null
    every { instrumentRepository.findBySymbol("BNKE:PAR:EUR") } returns Optional.of(etf)
    every { lightyearPriceClient.lookupUuid("BNKE:PAR") } returns LightyearUuidLookupResponse("BNKE:PAR", "uuid-bnke")
    every { lightyearPriceClient.getInstrumentBatch(listOf("uuid-bnke")) } returns
      listOf(response("uuid-bnke", "https://lightyear.test/amundi.png"))

    val logos = service.fetchLogoUrls(listOf("BNKE:PAR:EUR"))

    expect(logos).toEqual(mapOf("BNKE:PAR:EUR" to "https://lightyear.test/amundi.png"))
  }

  @Test
  fun `leaves out an instrument Lightyear has no logo for`() {
    stubConfiguredUuids()
    every { lightyearPriceClient.getInstrumentBatch(listOf("uuid-vwce")) } returns listOf(response("uuid-vwce", null))

    val logos = service.fetchLogoUrls(listOf("VWCE:GER:EUR"))

    expect(logos).toEqual(emptyMap())
  }

  @Test
  fun `reports the lookup as failed when Lightyear rejects the batch`() {
    stubConfiguredUuids()
    every { lightyearPriceClient.getInstrumentBatch(any()) } throws badRequest()

    val logos = service.fetchLogoUrls(listOf("VWCE:GER:EUR", "SXR8:GER:EUR"))

    expect(logos).toEqual(null)
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
