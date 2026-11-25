package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.model.holding.HoldingPositionRow
import ee.tenman.portfolio.model.holding.InternalHoldingData
import ee.tenman.portfolio.model.holding.toHoldingData
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.InstrumentTransactionData
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class SyntheticEtfCalculationService(
  private val dailyPriceService: DailyPriceService,
) {
  fun buildHoldings(
    positions: List<HoldingPositionRow>,
    etfSymbol: String,
    platformFilter: Set<Platform>?,
    instrumentsByTicker: Map<String, Instrument>,
    transactionData: Map<Long, InstrumentTransactionData>,
  ): List<InternalHoldingData> =
    positions.mapNotNull { position ->
      val instrument = position.ticker?.let { instrumentsByTicker[it] } ?: return@mapNotNull null
      val data = transactionData[instrument.id] ?: return@mapNotNull null
      val quantity = data.quantityForPlatforms(platformFilter)
      if (quantity <= BigDecimal.ZERO) return@mapNotNull null
      val value = quantity.multiply(dailyPriceService.getCurrentPrice(instrument))
      val holding = position.toHoldingData(value, etfSymbol, data.platformsForFilter(platformFilter))
      holding.copy(sector = holding.sector ?: cryptoSector(instrument))
    }

  private fun cryptoSector(instrument: Instrument): String? =
    IndustrySector.CRYPTOCURRENCY.displayName.takeIf { instrument.category.equals(InstrumentCategory.CRYPTO.name, ignoreCase = true) }
}
