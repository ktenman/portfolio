package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.model.holding.InternalHoldingData
import ee.tenman.portfolio.model.holding.SyntheticHoldingValue
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.transaction.TransactionCalculationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@Service
class SyntheticEtfCalculationService(
  private val instrumentRepository: InstrumentRepository,
  private val etfPositionRepository: EtfPositionRepository,
  private val transactionCalculationService: TransactionCalculationService,
  private val dailyPriceService: DailyPriceService,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

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
    val holdingValues = calculateHoldingValues(positions)
    return holdingValues.map { h ->
      InternalHoldingData(
        holdingUuid = h.position.holding.uuid,
        ticker =
          h.position.holding.ticker
          ?.uppercase()
          ?.trim()
          ?.takeIf { it.isNotBlank() },
          name =
            h.position.holding.name
          .trim(),
            sector =
              h.position.holding.sector
          ?.trim()
          ?.takeIf { it.isNotBlank() },
              countryCode =
                h.position.holding.countryCode
          ?.trim()
          ?.takeIf { it.isNotBlank() },
                countryName =
                  h.position.holding.countryName
          ?.trim()
          ?.takeIf { it.isNotBlank() },
                  value = h.value,
        etfSymbol = etfSymbol,
        platforms = h.platforms,
      )
    }
  }

  fun calculateHoldingValues(positions: List<EtfPosition>): List<SyntheticHoldingValue> {
    val tickers = positions.mapNotNull { it.holding.ticker }
    if (tickers.isEmpty()) return emptyList()
    val instrumentsByTicker = instrumentRepository.findBySymbolIn(tickers).associateBy { it.symbol }
    val instrumentIds = instrumentsByTicker.values.map { it.id }
    val transactionData = transactionCalculationService.batchCalculateAll(instrumentIds)
    return positions.mapNotNull { pos ->
      val ticker = pos.holding.ticker ?: return@mapNotNull null
      val instrument = instrumentsByTicker[ticker] ?: return@mapNotNull null
      val data = transactionData[instrument.id] ?: return@mapNotNull null
      val price = getCurrentPrice(instrument)
      val value = data.netQuantity.multiply(price)
      SyntheticHoldingValue(pos, value, data.platforms)
    }
  }

  fun calculateTotalValue(etfs: List<Instrument>): BigDecimal =
    etfs.fold(BigDecimal.ZERO) { acc, etf ->
      val positions = etfPositionRepository.findLatestPositionsByEtfId(etf.id)
      val holdingValues = calculateHoldingValues(positions)
      val syntheticValue = holdingValues.fold(BigDecimal.ZERO) { sum, h -> sum.add(h.value) }
      acc.add(syntheticValue)
    }

  private fun getCurrentPrice(instrument: Instrument): BigDecimal {
    instrument.currentPrice?.takeIf { it > BigDecimal.ZERO }?.let { return it }
    return runCatching { dailyPriceService.getPrice(instrument, LocalDate.now(clock)) }
      .onFailure { log.warn("No price found for ${instrument.symbol}, using zero", it) }
      .getOrDefault(BigDecimal.ZERO)
  }
}
