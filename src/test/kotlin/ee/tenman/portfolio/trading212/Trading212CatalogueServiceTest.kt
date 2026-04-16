package ee.tenman.portfolio.trading212

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class Trading212CatalogueServiceTest {
  private val client = mockk<Trading212CatalogueClient>()
  private val service = Trading212CatalogueService(client)

  @Test
  fun `should return instrument by exact ticker match`() {
    val instrument =
      Trading212Instrument(
        ticker = "SANe_EQ",
        type = "STOCK",
        isin = "ES0113900J37",
        currencyCode = "EUR",
        name = "Banco Santander",
        shortName = "SAN",
      )
    every { client.fetchInstruments() } returns listOf(instrument)
    val result = service.getInstrumentByTicker("SANe_EQ")
    expect(result).notToEqualNull()
    expect(result!!.name).toEqual("Banco Santander")
  }

  @Test
  fun `should return null when ticker not in catalogue`() {
    every { client.fetchInstruments() } returns emptyList()
    val result = service.getInstrumentByTicker("UNKNOWN_EQ")
    expect(result).toEqual(null)
  }

  @Test
  fun `should fetch catalogue once and serve from in-memory cache`() {
    every { client.fetchInstruments() } returns
      listOf(
        Trading212Instrument("SANe_EQ", "STOCK", "ES0113900J37", "EUR", "Banco Santander", "SAN"),
      )
    service.getInstrumentByTicker("SANe_EQ")
    service.getInstrumentByTicker("SANe_EQ")
    verify(exactly = 1) { client.fetchInstruments() }
  }

  @Test
  fun `cannot fail when upstream throws and returns null`() {
    every { client.fetchInstruments() } throws RuntimeException("429 rate limited")
    val result = service.getInstrumentByTicker("SANe_EQ")
    expect(result).toEqual(null)
  }
}
