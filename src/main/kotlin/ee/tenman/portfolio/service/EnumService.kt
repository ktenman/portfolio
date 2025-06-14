package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import org.springframework.stereotype.Service

@Service
class EnumService {
  fun getAllEnums(): Map<String, List<String>> =
    mapOf(
      "platforms" to getPlatforms(),
      "providers" to getProviders(),
      "transactionTypes" to getTransactionTypes(),
      "categories" to getCategories(),
      "currencies" to getCurrencies(),
    )

  private fun getPlatforms(): List<String> = Platform.entries.map { it.name }.sorted()

  private fun getProviders(): List<String> = ProviderName.entries.map { it.name }.sorted()

  private fun getTransactionTypes(): List<String> = TransactionType.entries.map { it.name }

  private fun getCategories(): List<String> = InstrumentCategory.entries.map { it.name }.sorted()

  private fun getCurrencies(): List<String> = Currency.entries.map { it.name }.sorted()
}
