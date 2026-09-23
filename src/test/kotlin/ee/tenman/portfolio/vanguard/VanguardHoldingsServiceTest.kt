package ee.tenman.portfolio.vanguard

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.GicsIndustry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class VanguardHoldingsServiceTest {
  private val vanguardHoldingsClient: VanguardHoldingsClient = mockk()
  private val service = VanguardHoldingsService(vanguardHoldingsClient)

  @Test
  fun `should request the country field provided by Vanguard`() {
    val requests = mutableListOf<VanguardHoldingsRequest>()
    every { vanguardHoldingsClient.getHoldings(capture(requests)) } returns response(listOf(item("Apple Inc", "AAPL", "100")))
    service.fetchHoldings(VGLA_PORT_ID)
    expect(requests.single().query).toContain("bloombergIsoCountry")
  }

  @Test
  fun `should preserve conflicting share class countries for reconciliation`() {
    stubSinglePage(
      listOf(item("Shopify Inc", "SHOP", "90", country = "CA"), item("SHOPIFY INC", "SHOP", "10", country = "US")),
    )
    val snapshot = service.fetchHoldings(VGLA_PORT_ID)
    expect(snapshot.countryCodes).toEqual(mapOf("Shopify Inc" to setOf("CA", "US")))
    expect(snapshot.holdings.single().countryCode).toEqual(null)
  }

  @Test
  fun `should normalize country codes while ignoring missing and invalid codes`() {
    stubSinglePage(
      listOf(
        item("Apple Inc", "AAPL", "60", country = " us "),
        item("Apple Inc", "AAPL", "10"),
        item("Microsoft Corp", "MSFT", "30", country = "ZZ"),
      ),
    )
    val snapshot = service.fetchHoldings(VGLA_PORT_ID)
    expect(snapshot.countryCodes).toEqual(mapOf("Apple Inc" to setOf("US"), "Microsoft Corp" to emptySet()))
  }

  @Test
  fun `cannot treat a zero weight share class as a country conflict`() {
    stubSinglePage(listOf(item("Ferrari NV", "RACE", "100", country = "IT"), item("Ferrari NV", "RACE", "0", country = "US")))
    expect(service.fetchHoldings(VGLA_PORT_ID).countryCodes).toEqual(mapOf("Ferrari NV" to setOf("IT")))
  }

  @Test
  fun `should follow the cursor until a page reports no further key`() {
    val requests = mutableListOf<VanguardHoldingsRequest>()
    every { vanguardHoldingsClient.getHoldings(capture(requests)) } returnsMany
      listOf(
        response(listOf(item("Apple Inc", "AAPL", "60")), lastItemKey = "page-2-key"),
        response(listOf(item("Microsoft Corp", "MSFT", "40"))),
      )

    service.fetchHoldings(VGLA_PORT_ID)

    expect(requests.map { it.variables.lastItemKey }).toContainExactly(null, "page-2-key")
  }

  @Test
  fun `should request the fund with its port id and the equity security types`() {
    val requests = mutableListOf<VanguardHoldingsRequest>()
    every { vanguardHoldingsClient.getHoldings(capture(requests)) } returns response(listOf(item("Apple Inc", "AAPL", "100")))

    service.fetchHoldings(VGLA_PORT_ID)

    expect(requests.single().variables.portIds).toContainExactly(VGLA_PORT_ID)
  }

  @Test
  fun `should return the effective date reported by the fund`() {
    stubSinglePage(listOf(item("Apple Inc", "AAPL", "100")))

    val snapshot = service.fetchHoldings(VGLA_PORT_ID)

    expect(snapshot.effectiveDate).toEqual(EFFECTIVE_DATE)
  }

  @Test
  fun `should merge share classes of the same issuer that differ only in case`() {
    stubSinglePage(
      listOf(
        item("Alphabet Inc", "GOOGL", "2"),
        item("ALPHABET INC", "GOOG", "1"),
        item("Microsoft Corp", "MSFT", "1"),
      ),
    )

    val snapshot = service.fetchHoldings(VGLA_PORT_ID)

    expect(snapshot.holdings.map { it.name }).toContainExactly("Alphabet Inc", "Microsoft Corp")
  }

  @Test
  fun `should sum the weights of merged share classes`() {
    stubSinglePage(
      listOf(item("Alphabet Inc", "GOOGL", "2"), item("alphabet inc", "GOOG", "1"), item("Microsoft Corp", "MSFT", "1")),
    )

    val snapshot = service.fetchHoldings(VGLA_PORT_ID)

    expect(snapshot.holdings.first { it.name == "Alphabet Inc" }.weight).toEqualNumerically(BigDecimal("75"))
  }

  @Test
  fun `should take the ticker of the share class with the largest weight`() {
    stubSinglePage(listOf(item("Roche Holding AG", "ROG", "1"), item("Roche Holding AG", "ROGP", "3")))

    val snapshot = service.fetchHoldings(VGLA_PORT_ID)

    expect(snapshot.holdings.single().ticker).toEqual("ROGP")
  }

  @Test
  fun `should break a weight tie between share classes by ticker`() {
    stubSinglePage(listOf(item("Schroders PLC", "SDRC", "2"), item("Schroders PLC", "SDR", "2")))

    val snapshot = service.fetchHoldings(VGLA_PORT_ID)

    expect(snapshot.holdings.single().ticker).toEqual("SDR")
  }

  @Test
  fun `should merge share classes reported on different pages`() {
    every { vanguardHoldingsClient.getHoldings(any()) } returnsMany
      listOf(
        response(listOf(item("Nestlé SA", "NESN", "3")), lastItemKey = "page-2-key"),
        response(listOf(item("NESTLÉ SA", "NSRGY", "1"))),
      )

    val snapshot = service.fetchHoldings(VGLA_PORT_ID)

    expect(snapshot.holdings).toHaveSize(1)
  }

  @Test
  fun `should keep leading zeros in tickers`() {
    stubSinglePage(listOf(item("Samsung Electronics Co Ltd", "005930", "100")))

    val snapshot = service.fetchHoldings(VXUS_PORT_ID)

    expect(snapshot.holdings.single().ticker).toEqual("005930")
  }

  @Test
  fun `should treat a blank ticker as absent`() {
    stubSinglePage(listOf(item("Tundmatu Ühistu OÜ", "  ", "100")))

    val snapshot = service.fetchHoldings(VXUS_PORT_ID)

    expect(snapshot.holdings.single().ticker).toEqual(null)
  }

  @Test
  fun `should normalize weights that do not cover the whole fund to one hundred percent`() {
    stubSinglePage(listOf(item("Apple Inc", "AAPL", "60"), item("Microsoft Corp", "MSFT", "30")))

    val snapshot = service.fetchHoldings(VXUS_PORT_ID)

    expect(snapshot.holdings.sumOf { it.weight }).toEqualNumerically(BigDecimal("100"))
  }

  @Test
  fun `should scale down weights that overlap to more than one hundred percent`() {
    stubSinglePage(listOf(item("Alphabet Inc", "GOOGL", "3.02968"), item("Rest Of Fund Ltd", "REST", "97.06459")))

    val alphabet = service.fetchHoldings(VGLA_PORT_ID).holdings.first { it.name == "Alphabet Inc" }

    expect(alphabet.weight.setScale(5, RoundingMode.HALF_UP)).toEqualNumerically(BigDecimal("3.02683"))
  }

  @Test
  fun `should rank issuers by descending weight`() {
    stubSinglePage(
      listOf(
        item("Microsoft Corp", "MSFT", "20"),
        item("Apple Inc", "AAPL", "30"),
        item("Nvidia Corp", "NVDA", "10"),
      ),
    )

    val snapshot = service.fetchHoldings(VGLA_PORT_ID)

    expect(snapshot.holdings.map { "${it.rank} ${it.name}" })
      .toContainExactly("1 Apple Inc", "2 Microsoft Corp", "3 Nvidia Corp")
  }

  @Test
  fun `should map a supplied GICS industry description`() {
    stubSinglePage(listOf(item("Apple Inc", "AAPL", "100", industry = "Technology Hardware, Storage & Peripherals")))

    val snapshot = service.fetchHoldings(VGLA_PORT_ID)

    expect(snapshot.holdings.single().industry).toEqual(GicsIndustry.TECHNOLOGY_HARDWARE_STORAGE_AND_PERIPHERALS)
  }

  @Test
  fun `should map the mortgage real estate investment trusts alias`() {
    stubSinglePage(
      listOf(item("Annaly Capital Management Inc", "NLY", "100", industry = "Mortgage Real Estate Investment Trusts (REITs)")),
    )

    val snapshot = service.fetchHoldings(VXUS_PORT_ID)

    expect(snapshot.holdings.single().industry).toEqual(GicsIndustry.MORTGAGE_REITS)
  }

  @Test
  fun `should leave the industry empty when the description is unknown`() {
    stubSinglePage(listOf(item("Apple Inc", "AAPL", "100", industry = "Kosmoselennundus")))

    val snapshot = service.fetchHoldings(VGLA_PORT_ID)

    expect(snapshot.holdings.single().industry).toEqual(null)
  }

  @Test
  fun `should take the industry of a merged share class that reports one`() {
    stubSinglePage(
      listOf(
        item("Roche Holding AG", "ROG", "3"),
        item("Roche Holding AG", "ROGP", "1", industry = "Pharmaceuticals"),
      ),
    )

    val snapshot = service.fetchHoldings(VGLA_PORT_ID)

    expect(snapshot.holdings.single().industry).toEqual(GicsIndustry.PHARMACEUTICALS)
  }

  @Test
  fun `cannot choose an authoritative industry from conflicting share classes`() {
    stubSinglePage(
      listOf(
        item("Roche Holding AG", "ROG", "3", industry = "Pharmaceuticals"),
        item("Roche Holding AG", "ROGP", "1", industry = "Biotechnology"),
      ),
    )

    expect { service.fetchHoldings(VGLA_PORT_ID) }.toThrow<IllegalStateException>()
  }

  @Test
  fun `should leave the sector empty for the sector classification job`() {
    stubSinglePage(listOf(item("Apple Inc", "AAPL", "100", industry = "Software")))

    val snapshot = service.fetchHoldings(VGLA_PORT_ID)

    expect(snapshot.holdings.single().sector).toEqual(null)
  }

  @Test
  fun `should throw when the fund reports GraphQL errors alongside a successful status`() {
    every { vanguardHoldingsClient.getHoldings(any()) } returns
      VanguardHoldingsResponse(errors = listOf(VanguardError("Unknown port id")))

    expect {
      service.fetchHoldings(VGLA_PORT_ID)
    }.toThrow<IllegalStateException>().messageToContain("Unknown port id")
  }

  @Test
  fun `should throw when the fund returns no holdings payload`() {
    every { vanguardHoldingsClient.getHoldings(any()) } returns VanguardHoldingsResponse(VanguardHoldingsPayload(emptyList()))

    expect {
      service.fetchHoldings(VGLA_PORT_ID)
    }.toThrow<IllegalStateException>().messageToContain("no holdings payload")
  }

  @Test
  fun `should throw when the fund returns no equity holdings`() {
    stubSinglePage(emptyList())

    expect {
      service.fetchHoldings(VGLA_PORT_ID)
    }.toThrow<IllegalStateException>().messageToContain("no equity holdings")
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = ["   "])
  fun `should throw when a weighted holding has no issuer name`(name: String?) {
    stubSinglePage(listOf(item(name, "AAPL", "100")))

    expect {
      service.fetchHoldings(VGLA_PORT_ID)
    }.toThrow<IllegalStateException>().messageToContain("without an issuer name")
  }

  @Test
  fun `should throw when a weight is missing`() {
    stubSinglePage(listOf(VanguardHoldingItem(issuerName = "Apple Inc", ticker = "AAPL", effectiveDate = EFFECTIVE_DATE)))

    expect {
      service.fetchHoldings(VGLA_PORT_ID)
    }.toThrow<IllegalStateException>().messageToContain("without a weight")
  }

  @Test
  fun `should throw when an effective date is missing`() {
    stubSinglePage(
      listOf(VanguardHoldingItem(issuerName = "Apple Inc", ticker = "AAPL", marketValuePercentage = BigDecimal.ONE)),
    )

    expect {
      service.fetchHoldings(VGLA_PORT_ID)
    }.toThrow<IllegalStateException>().messageToContain("without an effective date")
  }

  @Test
  fun `should throw when holdings report different effective dates`() {
    stubSinglePage(
      listOf(
        item("Apple Inc", "AAPL", "60"),
        item("Microsoft Corp", "MSFT", "40", effectiveDate = EFFECTIVE_DATE.plusDays(1)),
      ),
    )

    expect {
      service.fetchHoldings(VGLA_PORT_ID)
    }.toThrow<IllegalStateException>().messageToContain("mixed effective dates")
  }

  @Test
  fun `should throw when the total weight is not positive`() {
    stubSinglePage(listOf(item("Apple Inc", "AAPL", "-1")))

    expect {
      service.fetchHoldings(VGLA_PORT_ID)
    }.toThrow<IllegalStateException>().messageToContain("total weight")
  }

  @ParameterizedTest
  @ValueSource(strings = ["0", "0.00000", "0E+5"])
  fun `should drop zero weight holdings before validating their fields`(weight: String) {
    stubSinglePage(
      listOf(
        item("Apple Inc", "AAPL", "100"),
        VanguardHoldingItem(marketValuePercentage = BigDecimal(weight)),
        item("Hologic Inc", "HOLX", weight, effectiveDate = EFFECTIVE_DATE.minusMonths(1)),
      ),
    )
    val snapshot = service.fetchHoldings(VGLA_PORT_ID)
    expect(snapshot.holdings.map { it.name }).toContainExactly("Apple Inc")
  }

  @Test
  fun `should throw when all equity holdings have zero weight`() {
    stubSinglePage(listOf(item("Apple Inc", "AAPL", "0")))
    expect { service.fetchHoldings(VGLA_PORT_ID) }.toThrow<IllegalStateException>().messageToContain("no equity holdings")
  }

  @Test
  fun `should throw when the fund keeps returning cursors`() {
    every { vanguardHoldingsClient.getHoldings(any()) } returns
      response(listOf(item("Apple Inc", "AAPL", "100")), lastItemKey = "endless")

    expect {
      service.fetchHoldings(VGLA_PORT_ID)
    }.toThrow<IllegalStateException>().messageToContain("more than 20 holdings pages")
  }

  @Test
  fun `should return nothing when a later page fails`() {
    every { vanguardHoldingsClient.getHoldings(any()) } returns
      response(listOf(item("Apple Inc", "AAPL", "100")), lastItemKey = "page-2-key") andThenThrows
      IllegalArgumentException("Vanguard is unreachable")

    expect {
      service.fetchHoldings(VGLA_PORT_ID)
    }.toThrow<IllegalArgumentException>().messageToContain("Vanguard is unreachable")
  }

  private fun stubSinglePage(items: List<VanguardHoldingItem>) {
    every { vanguardHoldingsClient.getHoldings(any()) } returns response(items)
  }

  private fun response(
    items: List<VanguardHoldingItem>,
    lastItemKey: String? = null,
  ): VanguardHoldingsResponse =
    VanguardHoldingsResponse(
      data = VanguardHoldingsPayload(listOf(VanguardFund(VanguardHoldingsPage(items, lastItemKey)))),
    )

  private fun item(
    name: String?,
    ticker: String?,
    weight: String,
    industry: String? = null,
    effectiveDate: LocalDate = EFFECTIVE_DATE,
    country: String? = null,
  ): VanguardHoldingItem =
    VanguardHoldingItem(
      issuerName = name,
      ticker = ticker,
      marketValuePercentage = BigDecimal(weight),
      gicsIndustryDescription = industry,
      effectiveDate = effectiveDate,
      bloombergIsoCountry = country,
    )

  companion object {
    private const val VGLA_PORT_ID = "E161"
    private const val VXUS_PORT_ID = "E165"
    private val EFFECTIVE_DATE = LocalDate.of(2026, 8, 31)
  }
}
