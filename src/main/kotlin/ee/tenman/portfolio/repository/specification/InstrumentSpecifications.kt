package ee.tenman.portfolio.repository.specification

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import org.springframework.data.jpa.domain.Specification

object InstrumentSpecifications {
  val ETF_PROVIDERS = listOf(ProviderName.LIGHTYEAR, ProviderName.FT, ProviderName.SYNTHETIC)

  fun hasProviderNameIn(providers: List<ProviderName>): Specification<Instrument> =
    Specification { root, _, _ ->
      root.get<ProviderName>("providerName").`in`(providers)
    }

  fun hasSymbolIn(symbols: List<String>): Specification<Instrument> =
    Specification { root, _, _ ->
      root.get<String>("symbol").`in`(symbols)
    }

  fun hasTransactionsOnPlatforms(platforms: Set<Platform>): Specification<Instrument> =
    Specification { root, query, cb ->
      val subquery = query?.subquery(Long::class.java) ?: return@Specification cb.conjunction()
      val txRoot = subquery.from(PortfolioTransaction::class.java)
      subquery
        .select(txRoot.get<Instrument>("instrument").get("id"))
        .where(txRoot.get<Platform>("platform").`in`(platforms))
      cb.`in`(root.get<Long>("id")).value(subquery)
    }
}
