package ee.tenman.portfolio.service.monitoring

import ee.tenman.portfolio.configuration.LightyearScrapingProperties
import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.job.CsusHoldingsRetrievalJob
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.vanguard.VanguardHoldingsService
import org.springframework.stereotype.Service

@Service
class CollectionInventoryService(
  private val repository: InstrumentRepository,
  private val lightyear: LightyearScrapingProperties,
  private val trading212: Trading212ScrapingProperties,
) {
  fun configured(): Map<CollectionKey, Set<String>> {
    val instruments =
      repository
        .findByProviderNameIn(listOf(ProviderName.TRADING212, ProviderName.BINANCE, ProviderName.FT, ProviderName.LIGHTYEAR))
        .groupBy { it.providerName }
    val symbols = instruments.mapValues { (_, items) -> items.mapTo(linkedSetOf()) { it.symbol } }
    val trading = symbols[ProviderName.TRADING212].orEmpty()
    return mapOf(
      CollectionKey.LIGHTYEAR_PRICES to lightyear.getAllSymbols().toSet(),
      CollectionKey.TRADING212_PRICES to trading,
      CollectionKey.BINANCE_PRICES to symbols[ProviderName.BINANCE].orEmpty(),
      CollectionKey.FT_HISTORY to symbols[ProviderName.FT].orEmpty(),
      CollectionKey.LIGHTYEAR_HISTORY to symbols[ProviderName.LIGHTYEAR].orEmpty(),
    ) + holdings(trading)
  }

  private fun holdings(trading: Set<String>): Map<CollectionKey, Set<String>> =
    mapOf(
      CollectionKey.LIGHTYEAR_HOLDINGS to lightyear.getHoldingsSymbols(),
      CollectionKey.TRADING212_HOLDINGS to trading212.symbols.map { it.symbol }.intersect(trading),
      CollectionKey.BLACKROCK_HOLDINGS to setOf(CsusHoldingsRetrievalJob.AVIVA_SYMBOL),
      CollectionKey.VANGUARD_HOLDINGS to VanguardHoldingsService.FUNDS.keys,
    )
}
