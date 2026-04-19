package ee.tenman.portfolio.service.instrument

import ee.tenman.portfolio.configuration.FundCurrencyOverridesProperties
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.lightyear.LightyearFundInfoData
import org.springframework.stereotype.Service

@Service
class FundCurrencyResolverService(
  private val overrides: FundCurrencyOverridesProperties,
  private val llmLookup: FundCurrencyLlmLookupService,
) {
  fun resolve(
    instrument: Instrument,
    lightyearData: LightyearFundInfoData?,
  ): Currency? =
    overrides.forSymbol(instrument.symbol)
      ?: lightyearData?.fundCurrency
      ?: instrument.fundCurrency
      ?: llmLookup.lookup(instrument)
}
