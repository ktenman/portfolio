package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.model.holding.InternalHoldingData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class HoldingAggregationServiceTest {
  private lateinit var service: HoldingAggregationService

  @BeforeEach
  fun setup() {
    service = HoldingAggregationService()
  }

  @Nested
  inner class AggregateHoldings {
    @Test
    fun `should return empty map for empty list`() {
      val result = service.aggregateHoldings(emptyList())

      expect(result.size).toEqual(0)
    }

    @Test
    fun `should aggregate single holding`() {
      val holding = createHolding("Apple Inc", "AAPL", BigDecimal("100.00"), "VWCE")

      val result = service.aggregateHoldings(listOf(holding))

      expect(result.size).toEqual(1)
      val entry = result.entries.first()
      expect(entry.key.name).toEqual("Apple Inc")
      expect(entry.key.ticker).toEqual("AAPL")
      expect(entry.value.totalValue).toEqualNumerically(BigDecimal("100.00"))
      expect(entry.value.etfSymbols).toContainExactly("VWCE")
    }

    @Test
    fun `should aggregate holdings with same normalized name from different ETFs`() {
      val holdings =
        listOf(
          createHolding("Apple Inc", "AAPL", BigDecimal("100.00"), "VWCE"),
          createHolding("Apple Inc", "AAPL", BigDecimal("50.00"), "VUAA"),
        )

      val result = service.aggregateHoldings(holdings)

      expect(result.size).toEqual(1)
      val entry = result.entries.first()
      expect(entry.value.totalValue).toEqualNumerically(BigDecimal("150.00"))
      expect(entry.value.etfSymbols.size).toEqual(2)
    }

    @Test
    fun `should keep separate entries for different companies`() {
      val holdings =
        listOf(
          createHolding("Apple Inc", "AAPL", BigDecimal("100.00"), "VWCE"),
          createHolding("Microsoft Corp", "MSFT", BigDecimal("80.00"), "VWCE"),
        )

      val result = service.aggregateHoldings(holdings)

      expect(result.size).toEqual(2)
    }

    @Test
    fun `should select longest ticker when aggregating`() {
      val holdings =
        listOf(
          createHolding("NVIDIA Corp", "NVDA", BigDecimal("50.00"), "VWCE"),
          createHolding("NVIDIA Corp", null, BigDecimal("30.00"), "VUAA"),
        )

      val result = service.aggregateHoldings(holdings)

      expect(result.size).toEqual(1)
      val entry = result.entries.first()
      expect(entry.key.ticker).toEqual("NVDA")
    }

    @Test
    fun `should merge platforms from different holdings`() {
      val holdings =
        listOf(
          createHolding("Apple Inc", "AAPL", BigDecimal("100.00"), "VWCE", setOf(Platform.TRADING212)),
          createHolding("Apple Inc", "AAPL", BigDecimal("50.00"), "VUAA", setOf(Platform.LIGHTYEAR)),
        )

      val result = service.aggregateHoldings(holdings)

      expect(result.size).toEqual(1)
      val entry = result.entries.first()
      expect(entry.value.platforms.size).toEqual(2)
    }
  }

  @Nested
  inner class NormalizeHoldingName {
    @Test
    fun `should keep company name with Inc suffix`() {
      expect(service.normalizeHoldingName("Apple Inc")).toEqual("apple inc")
    }

    @Test
    fun `should keep company name with Corp suffix`() {
      expect(service.normalizeHoldingName("Microsoft Corp")).toEqual("microsoft corp")
    }

    @Test
    fun `should keep company name with Ltd suffix`() {
      expect(service.normalizeHoldingName("British Ltd")).toEqual("british ltd")
    }

    @Test
    fun `should keep company name with LLC suffix`() {
      expect(service.normalizeHoldingName("Acme LLC")).toEqual("acme llc")
    }

    @Test
    fun `should keep dot com suffix`() {
      expect(service.normalizeHoldingName("Amazon.com")).toEqual("amazon.com")
    }

    @Test
    fun `should keep full company name`() {
      expect(service.normalizeHoldingName("Acme Holdings Inc")).toEqual("acme holdings inc")
    }

    @Test
    fun `should normalize whitespace`() {
      expect(service.normalizeHoldingName("  Company   Name  ")).toEqual("company name")
    }

    @Test
    fun `should convert to lowercase`() {
      expect(service.normalizeHoldingName("APPLE")).toEqual("apple")
    }

    @Test
    fun `should keep Class A suffix`() {
      expect(service.normalizeHoldingName("Alphabet Class A")).toEqual("alphabet class a")
    }

    @Test
    fun `should keep ADR suffix`() {
      expect(service.normalizeHoldingName("Taiwan Semi Spon ADR")).toEqual("taiwan semi spon adr")
    }

    @Test
    fun `should keep Holdings as company name`() {
      expect(service.normalizeHoldingName("Holdings")).toEqual("holdings")
    }
  }

  private fun createHolding(
    name: String,
    ticker: String?,
    value: BigDecimal,
    etfSymbol: String,
    platforms: Set<Platform> = setOf(Platform.TRADING212),
  ): InternalHoldingData =
    InternalHoldingData(
      holdingUuid = UUID.randomUUID(),
      ticker = ticker,
      name = name,
      sector = null,
      countryCode = null,
      countryName = null,
      value = value,
      etfSymbol = etfSymbol,
      platforms = platforms,
    )
}
