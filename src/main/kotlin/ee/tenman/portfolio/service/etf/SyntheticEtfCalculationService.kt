package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.model.holding.InternalHoldingData
import ee.tenman.portfolio.model.holding.SyntheticHoldingValue
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.TransactionCalculationService
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class SyntheticEtfCalculationService(
  private val instrumentRepository: InstrumentRepository,
  private val etfPositionRepository: EtfPositionRepository,
  private val transactionCalculationService: TransactionCalculationService,
  private val dailyPriceService: DailyPriceService,
) {
  fun hasActiveHoldings(syntheticEtfId: Long): Boolean {
    val positions = etfPositionRepository.findLatestPositionsByEtfId(syntheticEtfId)
    val tickers = positions.mapNotNull { it.holding.ticker }
    if (tickers.isEmpty()) return false
    val instruments = instrumentRepository.findBySymbolIn(tickers)
    if (instruments.isEmpty()) return false
    val quantities = transactionCalculationService.batchCalculateNetQuantities(instruments.map { it.id })
    return quantities.values.any { it > BigDecimal.ZERO }
  }

  fun buildHoldings(
    positions: List<EtfPosition>,
    etfSymbol: String,
  ): List<InternalHoldingData> {
    val (holdingValues, instrumentsByTicker) = calculateHoldingValuesWithInstruments(positions)
    return holdingValues.map { h ->
      val holding = h.position.holding
      val ticker = holding.ticker
      val instrument = ticker?.let { instrumentsByTicker[it] }
      InternalHoldingData(
        holdingUuid = holding.uuid,
        ticker = ticker?.uppercase()?.trim()?.takeIf { it.isNotBlank() },
        name = holding.name.trim(),
        sector = resolveSector(holding.sector, instrument),
        countryCode = holding.countryCode?.trim()?.takeIf { it.isNotBlank() },
        countryName = holding.countryName?.trim()?.takeIf { it.isNotBlank() },
        value = h.value,
        etfSymbol = etfSymbol,
        platforms = h.platforms,
      )
    }
  }

  private fun resolveSector(
    holdingSector: String?,
    instrument: Instrument?,
  ): String? =
    holdingSector?.trim()?.takeIf { it.isNotBlank() }
      ?: instrument
        ?.category
        ?.takeIf { it.equals(InstrumentCategory.CRYPTO.name, ignoreCase = true) }
        ?.let { IndustrySector.CRYPTOCURRENCY.displayName }

  fun calculateHoldingValues(positions: List<EtfPosition>): List<SyntheticHoldingValue> =
    calculateHoldingValuesWithInstruments(positions).first

  private fun calculateHoldingValuesWithInstruments(
    positions: List<EtfPosition>,
  ): Pair<List<SyntheticHoldingValue>, Map<String, Instrument>> {
    val tickers = positions.mapNotNull { it.holding.ticker }
    if (tickers.isEmpty()) return Pair(emptyList(), emptyMap())
    val instrumentsByTicker = instrumentRepository.findBySymbolIn(tickers).associateBy { it.symbol }
    val transactionData = transactionCalculationService.batchCalculateAll(instrumentsByTicker.values.map { it.id })
    val holdingValues =
      positions.mapNotNull { pos ->
        val ticker = pos.holding.ticker ?: return@mapNotNull null
        val instrument = instrumentsByTicker[ticker] ?: return@mapNotNull null
        val data = transactionData[instrument.id] ?: return@mapNotNull null
        val price = dailyPriceService.getCurrentPrice(instrument)
        val value = data.netQuantity.multiply(price)
        SyntheticHoldingValue(pos, value, data.platforms)
      }
    return Pair(holdingValues, instrumentsByTicker)
  }

  fun calculateTotalValue(etfs: List<Instrument>): BigDecimal {
    if (etfs.isEmpty()) return BigDecimal.ZERO
    val allPositions = etfPositionRepository.findLatestPositionsByEtfIds(etfs.map { it.id })
    val positionsByEtfId = allPositions.groupBy { it.etfInstrument.id }
    return etfs.fold(BigDecimal.ZERO) { acc, etf ->
      val positions = positionsByEtfId[etf.id] ?: emptyList()
      val holdingValues = calculateHoldingValues(positions)
      val syntheticValue = holdingValues.fold(BigDecimal.ZERO) { sum, h -> sum.add(h.value) }
      acc.add(syntheticValue)
    }
  }
}
