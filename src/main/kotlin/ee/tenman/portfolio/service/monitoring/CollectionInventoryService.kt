package ee.tenman.portfolio.service.monitoring

import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.job.CsusHoldingsRetrievalJob
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.tuleva.TulevaHoldingsService
import ee.tenman.portfolio.vanguard.VanguardHoldingsService
import org.springframework.stereotype.Service

@Service
class CollectionInventoryService(
  private val repository: InstrumentRepository,
  private val lightyear: LightyearScrapingProperties,
  private val trading212: Trading212ScrapingProperties,
) {
  fun configured(): Map<CollectionKey, Set<String>> {
    val symbols =
      repository
        .findByProviderNameIn(DATABASE_COLLECTIONS.values.toList())
        .groupBy({ it.providerName }, { it.symbol })
        .mapValues { it.value.toSet() }
    val trading = symbols[ProviderName.TRADING212].orEmpty()
    return DATABASE_COLLECTIONS.mapValues { (_, provider) -> symbols[provider].orEmpty() } +
      (CollectionKey.LIGHTYEAR_PRICES to lightyear.getAllSymbols().toSet()) +
      holdings(trading)
  }

  private fun holdings(trading: Set<String>): Map<CollectionKey, Set<String>> =
    mapOf(
      CollectionKey.LIGHTYEAR_HOLDINGS to lightyear.getHoldingsSymbols(),
      CollectionKey.TRADING212_HOLDINGS to trading212.symbols.map { it.symbol }.intersect(trading),
      CollectionKey.BLACKROCK_HOLDINGS to setOf(CsusHoldingsRetrievalJob.AVIVA_SYMBOL),
      CollectionKey.VANGUARD_HOLDINGS to VanguardHoldingsService.FUNDS.keys,
      CollectionKey.TULEVA_HOLDINGS to setOf(TulevaHoldingsService.SYMBOL),
    )

  companion object {
    private val DATABASE_COLLECTIONS =
      mapOf(
        CollectionKey.TRADING212_PRICES to ProviderName.TRADING212,
        CollectionKey.BINANCE_PRICES to ProviderName.BINANCE,
        CollectionKey.FT_HISTORY to ProviderName.FT,
        CollectionKey.LIGHTYEAR_HISTORY to ProviderName.LIGHTYEAR,
        CollectionKey.TULEVA_HISTORY to ProviderName.TULEVA,
      )
  }
}
