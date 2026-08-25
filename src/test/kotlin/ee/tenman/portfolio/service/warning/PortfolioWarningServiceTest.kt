package ee.tenman.portfolio.service.warning

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.dto.EtfHoldingBreakdownDto
import ee.tenman.portfolio.dto.FundValue
import ee.tenman.portfolio.dto.PortfolioWarningDto
import ee.tenman.portfolio.dto.PortfolioWarningRule
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PortfolioWarningServiceTest {
  private val service = PortfolioWarningService(mockk(), mockk(), mockk())

  @Nested
  inner class LargestHolding {
    @Test
    fun `should report the biggest look-through holding by name`() {
      val warnings =
        service.evaluate(
          listOf(holding("Nestlé S.A.", "4.20"), holding("Sanofi", "7.80")),
          emptyMap(),
          emptyList(),
        )

      expect(warnings.of(PortfolioWarningRule.LARGEST_HOLDING).detail).toEqual("Sanofi")
    }

    @Test
    fun `should not breach when the largest holding sits exactly on the threshold`() {
      val warnings = service.evaluate(listOf(holding("Sanofi", "10")), emptyMap(), emptyList())

      expect(warnings.of(PortfolioWarningRule.LARGEST_HOLDING).breached).toEqual(false)
    }

    @Test
    fun `should breach when the largest holding is a hair above the threshold`() {
      val warnings = service.evaluate(listOf(holding("Sanofi", "10.0001")), emptyMap(), emptyList())

      expect(warnings.of(PortfolioWarningRule.LARGEST_HOLDING).breached).toEqual(true)
    }

    @Test
    fun `should omit every holding rule when there are no holdings`() {
      val warnings = service.evaluate(emptyList(), emptyMap(), emptyList())

      expect(warnings).toEqual(emptyList())
    }
  }

  @Nested
  inner class SectorConcentration {
    @Test
    fun `should sum the percentages of holdings sharing a sector`() {
      val warnings =
        service.evaluate(
          listOf(
            holding("Sanofi", "20", sector = "Health"),
            holding("Novo Nordisk", "15.0001", sector = "Health"),
            holding("Total", "30", sector = "Energy"),
          ),
          emptyMap(),
          emptyList(),
        )

      expect(warnings.of(PortfolioWarningRule.SECTOR_CONCENTRATION).measuredPercentage)
        .toEqualNumerically(BigDecimal("35.0001"))
    }

    @Test
    fun `should not breach when the largest sector sits exactly on the threshold`() {
      val warnings =
        service.evaluate(
          listOf(holding("Sanofi", "35", sector = "Health"), holding("Total", "30", sector = "Energy")),
          emptyMap(),
          emptyList(),
        )

      expect(warnings.of(PortfolioWarningRule.SECTOR_CONCENTRATION).breached).toEqual(false)
    }

    @Test
    fun `should ignore unclassified holdings when picking the largest sector`() {
      val warnings =
        service.evaluate(
          listOf(holding("Mystery Corp", "90"), holding("Sanofi", "10", sector = "Health")),
          emptyMap(),
          emptyList(),
        )

      expect(warnings.of(PortfolioWarningRule.SECTOR_CONCENTRATION).detail).toEqual("Health")
    }

    @Test
    fun `should omit the sector rule when nothing is classified`() {
      val warnings = service.evaluate(listOf(holding("Mystery Corp", "90")), emptyMap(), emptyList())

      expect(warnings.map { it.rule }).toEqual(listOf(PortfolioWarningRule.LARGEST_HOLDING))
    }
  }

  @Nested
  inner class CountryConcentration {
    @Test
    fun `should not breach when the largest country sits exactly on the threshold`() {
      val warnings =
        service.evaluate(
          listOf(holding("Apple", "70", country = "United States"), holding("Sanofi", "30", country = "France")),
          emptyMap(),
          emptyList(),
        )

      expect(warnings.of(PortfolioWarningRule.COUNTRY_CONCENTRATION).breached).toEqual(false)
    }

    @Test
    fun `should breach when the largest country is a hair above the threshold`() {
      val warnings =
        service.evaluate(
          listOf(holding("Apple", "70.0001", country = "United States"), holding("Sanofi", "29", country = "France")),
          emptyMap(),
          emptyList(),
        )

      expect(warnings.of(PortfolioWarningRule.COUNTRY_CONCENTRATION).breached).toEqual(true)
    }
  }

  @Nested
  inner class PlatformConcentration {
    @Test
    fun `should express the biggest platform as a share of all platform values`() {
      val warnings =
        service.evaluate(
          listOf(holding("Sanofi", "100")),
          mapOf("LIGHTYEAR" to BigDecimal("600"), "TRADING212" to BigDecimal("400")),
          emptyList(),
        )

      expect(warnings.of(PortfolioWarningRule.PLATFORM_CONCENTRATION).measuredPercentage)
        .toEqualNumerically(BigDecimal("60"))
    }

    @Test
    fun `should name the biggest platform by its display name`() {
      val warnings =
        service.evaluate(
          listOf(holding("Sanofi", "100")),
          mapOf("LIGHTYEAR" to BigDecimal("400"), "TRADING212" to BigDecimal("600")),
          emptyList(),
        )

      expect(warnings.of(PortfolioWarningRule.PLATFORM_CONCENTRATION).detail).toEqual("Trading 212")
    }

    @Test
    fun `should not breach when the biggest platform sits exactly on the threshold`() {
      val warnings =
        service.evaluate(
          listOf(holding("Sanofi", "100")),
          mapOf("LIGHTYEAR" to BigDecimal("600"), "TRADING212" to BigDecimal("400")),
          emptyList(),
        )

      expect(warnings.of(PortfolioWarningRule.PLATFORM_CONCENTRATION).breached).toEqual(false)
    }

    @Test
    fun `should breach when everything sits on a single platform`() {
      val warnings =
        service.evaluate(listOf(holding("Sanofi", "100")), mapOf("LHV" to BigDecimal("1000")), emptyList())

      expect(warnings.of(PortfolioWarningRule.PLATFORM_CONCENTRATION).breached).toEqual(true)
    }

    @Test
    fun `should omit the platform rule when no platform carries any value`() {
      val warnings =
        service.evaluate(listOf(holding("Sanofi", "100")), mapOf("LHV" to BigDecimal.ZERO), emptyList())

      expect(warnings.map { it.rule }).toEqual(listOf(PortfolioWarningRule.LARGEST_HOLDING))
    }
  }

  @Nested
  inner class AverageTer {
    @Test
    fun `should weight each fund fee by the value held in that fund`() {
      val warnings =
        service.evaluate(
          listOf(holding("Sanofi", "100")),
          emptyMap(),
          listOf(fund("1000", ter = "0.20"), fund("3000", ter = "0.50")),
        )

      expect(warnings.of(PortfolioWarningRule.AVERAGE_TER).measuredPercentage)
        .toEqualNumerically(BigDecimal("0.425"))
    }

    @Test
    fun `should not breach when the weighted fee sits exactly on the threshold`() {
      val warnings =
        service.evaluate(
          listOf(holding("Sanofi", "100")),
          emptyMap(),
          listOf(fund("1000", ter = "0.30"), fund("1000", ter = "0.50")),
        )

      expect(warnings.of(PortfolioWarningRule.AVERAGE_TER).breached).toEqual(false)
    }

    @Test
    fun `should ignore funds without a published fee`() {
      val warnings =
        service.evaluate(
          listOf(holding("Sanofi", "100")),
          emptyMap(),
          listOf(fund("9000"), fund("1000", ter = "0.22")),
        )

      expect(warnings.of(PortfolioWarningRule.AVERAGE_TER).measuredPercentage)
        .toEqualNumerically(BigDecimal("0.22"))
    }

    @Test
    fun `should omit the fee rule when no fund publishes a fee`() {
      val warnings = service.evaluate(listOf(holding("Sanofi", "100")), emptyMap(), listOf(fund("9000")))

      expect(warnings.map { it.rule }).toEqual(listOf(PortfolioWarningRule.LARGEST_HOLDING))
    }
  }

  @Nested
  inner class CurrencyExposure {
    @Test
    fun `should measure the biggest non-euro fund currency against all denominated value`() {
      val warnings =
        service.evaluate(
          listOf(holding("Sanofi", "100")),
          emptyMap(),
          listOf(fund("80", currency = Currency.EUR), fund("120", currency = Currency.USD)),
        )

      expect(warnings.of(PortfolioWarningRule.CURRENCY_EXPOSURE).measuredPercentage)
        .toEqualNumerically(BigDecimal("60"))
    }

    @Test
    fun `should breach when a single non-euro currency passes the threshold`() {
      val warnings =
        service.evaluate(
          listOf(holding("Sanofi", "100")),
          emptyMap(),
          listOf(fund("79", currency = Currency.EUR), fund("121", currency = Currency.USD)),
        )

      expect(warnings.of(PortfolioWarningRule.CURRENCY_EXPOSURE).breached).toEqual(true)
    }

    @Test
    fun `should omit the currency rule when everything is denominated in euro`() {
      val warnings =
        service.evaluate(
          listOf(holding("Sanofi", "100")),
          emptyMap(),
          listOf(fund("200", currency = Currency.EUR)),
        )

      expect(warnings.map { it.rule }).toEqual(listOf(PortfolioWarningRule.LARGEST_HOLDING))
    }

    @Test
    fun `should omit the currency rule when no fund declares a currency`() {
      val warnings = service.evaluate(listOf(holding("Sanofi", "100")), emptyMap(), listOf(fund("200")))

      expect(warnings.map { it.rule }).toEqual(listOf(PortfolioWarningRule.LARGEST_HOLDING))
    }
  }

  @Test
  fun `should carry the threshold of every evaluated rule`() {
    val warnings =
      service.evaluate(
        listOf(holding("Sanofi", "5", sector = "Health", country = "France")),
        mapOf("LHV" to BigDecimal("100")),
        listOf(fund("100", ter = "0.22", currency = Currency.USD)),
      )

    expect(warnings.map { it.thresholdPercentage }).toEqual(warnings.map { it.rule.threshold })
  }

  private fun List<PortfolioWarningDto>.of(rule: PortfolioWarningRule): PortfolioWarningDto = first { it.rule == rule }

  private fun holding(
    name: String,
    percentage: String,
    sector: String? = null,
    country: String? = null,
  ) = EtfHoldingBreakdownDto(
    holdingUuid = null,
    holdingTicker = null,
    holdingName = name,
    percentageOfTotal = BigDecimal(percentage),
    totalValueEur = BigDecimal(percentage),
    holdingSector = sector,
    holdingCountryCode = null,
    holdingCountryName = country,
    inEtfs = "VWCE",
    numEtfs = 1,
    platforms = "LHV",
  )

  private fun fund(
    value: String,
    ter: String? = null,
    currency: Currency? = null,
  ) = FundValue(BigDecimal(value), ter?.let { BigDecimal(it) }, currency)
}
