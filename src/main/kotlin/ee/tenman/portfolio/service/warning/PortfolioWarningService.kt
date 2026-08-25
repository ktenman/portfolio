package ee.tenman.portfolio.service.warning

import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.dto.EtfHoldingBreakdownDto
import ee.tenman.portfolio.dto.FundValue
import ee.tenman.portfolio.dto.PortfolioWarningDto
import ee.tenman.portfolio.dto.PortfolioWarningRule
import ee.tenman.portfolio.service.etf.EtfBreakdownDataLoaderService
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PortfolioWarningService(
  private val etfBreakdownService: EtfBreakdownService,
  private val dataLoader: EtfBreakdownDataLoaderService,
  private val dailyPriceService: DailyPriceService,
) {
  companion object {
    private const val SCALE = 4
    private val HUNDRED = BigDecimal(100)
  }

  @Transactional(readOnly = true)
  fun getWarnings(
    etfSymbols: List<String>?,
    platforms: List<String>?,
  ): List<PortfolioWarningDto> {
    val holdings = etfBreakdownService.getHoldingsBreakdown(etfSymbols, platforms)
    if (holdings.isEmpty()) return emptyList()
    return evaluate(holdings, valuePerPlatform(holdings, etfSymbols), fundValues(etfSymbols, platforms))
  }

  fun evaluate(
    holdings: List<EtfHoldingBreakdownDto>,
    valueByPlatform: Map<String, BigDecimal>,
    funds: List<FundValue>,
  ): List<PortfolioWarningDto> =
    listOfNotNull(
      largestHolding(holdings),
      largestGroup(holdings, PortfolioWarningRule.SECTOR_CONCENTRATION) { it.holdingSector },
      largestGroup(holdings, PortfolioWarningRule.COUNTRY_CONCENTRATION) { it.holdingCountryName },
      largestPlatform(valueByPlatform),
      weightedTer(funds),
      currencyExposure(funds),
    )

  private fun largestHolding(holdings: List<EtfHoldingBreakdownDto>): PortfolioWarningDto? {
    val largest = holdings.maxByOrNull { it.percentageOfTotal } ?: return null
    return warning(PortfolioWarningRule.LARGEST_HOLDING, largest.holdingName, largest.percentageOfTotal)
  }

  private fun largestGroup(
    holdings: List<EtfHoldingBreakdownDto>,
    rule: PortfolioWarningRule,
    key: (EtfHoldingBreakdownDto) -> String?,
  ): PortfolioWarningDto? {
    val largest =
      holdings
        .mapNotNull { holding -> key(holding)?.trim()?.takeIf { it.isNotBlank() }?.to(holding.percentageOfTotal) }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, percentages) -> percentages.fold(BigDecimal.ZERO, BigDecimal::add) }
        .maxByOrNull { it.value } ?: return null
    return warning(rule, largest.key, largest.value)
  }

  private fun largestPlatform(valueByPlatform: Map<String, BigDecimal>): PortfolioWarningDto? {
    val total = valueByPlatform.values.fold(BigDecimal.ZERO, BigDecimal::add)
    if (total <= BigDecimal.ZERO) return null
    val largest = valueByPlatform.maxByOrNull { it.value } ?: return null
    val name = Platform.fromStringOrNull(largest.key)?.displayName ?: largest.key
    return warning(PortfolioWarningRule.PLATFORM_CONCENTRATION, name, percentage(largest.value, total))
  }

  private fun weightedTer(funds: List<FundValue>): PortfolioWarningDto? {
    val withTer = funds.mapNotNull { fund -> fund.ter?.let { fund.value to it } }
    val total = withTer.fold(BigDecimal.ZERO) { acc, (value, _) -> acc.add(value) }
    if (total <= BigDecimal.ZERO) return null
    val weighted = withTer.fold(BigDecimal.ZERO) { acc, (value, ter) -> acc.add(value.multiply(ter)) }
    return warning(PortfolioWarningRule.AVERAGE_TER, null, weighted.divide(total, SCALE, RoundingMode.HALF_UP))
  }

  private fun currencyExposure(funds: List<FundValue>): PortfolioWarningDto? {
    val withCurrency = funds.mapNotNull { fund -> fund.currency?.let { it to fund.value } }
    val total = withCurrency.fold(BigDecimal.ZERO) { acc, (_, value) -> acc.add(value) }
    if (total <= BigDecimal.ZERO) return null
    val largest =
      withCurrency
        .filter { it.first != Currency.EUR }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.fold(BigDecimal.ZERO, BigDecimal::add) }
        .maxByOrNull { it.value } ?: return null
    return warning(PortfolioWarningRule.CURRENCY_EXPOSURE, largest.key.name, percentage(largest.value, total))
  }

  private fun warning(
    rule: PortfolioWarningRule,
    detail: String?,
    measured: BigDecimal,
  ): PortfolioWarningDto {
    val scaled = measured.setScale(SCALE, RoundingMode.HALF_UP)
    return PortfolioWarningDto(
      rule = rule,
      label = rule.label,
      detail = detail,
      measuredPercentage = scaled,
      thresholdPercentage = rule.threshold,
      breached = scaled > rule.threshold,
    )
  }

  private fun percentage(
    value: BigDecimal,
    total: BigDecimal,
  ): BigDecimal = value.multiply(HUNDRED).divide(total, SCALE, RoundingMode.HALF_UP)

  private fun valuePerPlatform(
    holdings: List<EtfHoldingBreakdownDto>,
    etfSymbols: List<String>?,
  ): Map<String, BigDecimal> {
    val names =
      holdings
        .flatMap { it.platforms.split(",") }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val total = holdings.fold(BigDecimal.ZERO) { acc, holding -> acc.add(holding.totalValueEur) }
    if (names.size <= 1) return names.associateWith { total }
    return names.associateWith { name ->
      etfBreakdownService
        .getHoldingsBreakdown(etfSymbols, listOf(name))
        .fold(BigDecimal.ZERO) { acc, holding -> acc.add(holding.totalValueEur) }
    }
  }

  private fun fundValues(
    etfSymbols: List<String>?,
    platforms: List<String>?,
  ): List<FundValue> {
    val filter = platforms?.mapNotNull { Platform.fromStringOrNull(it) }?.toSet()?.takeIf { it.isNotEmpty() }
    val data = dataLoader.loadBreakdownData(etfSymbols, filter)
    return data.instruments
      .filter { it.providerName != ProviderName.SYNTHETIC }
      .mapNotNull { etf ->
        val quantity = data.allTransactionData[etf.id]?.quantityForPlatforms(filter) ?: BigDecimal.ZERO
        if (quantity <= BigDecimal.ZERO) return@mapNotNull null
        FundValue(quantity.multiply(dailyPriceService.getCurrentPrice(etf)), etf.ter, etf.fundCurrency)
      }
  }
}
