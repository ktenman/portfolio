package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.model.holding.HoldingPositionRow
import ee.tenman.portfolio.model.holding.InternalHoldingData
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.transaction.InstrumentTransactionData
import ee.tenman.portfolio.service.transaction.TransactionCalculationService
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class EtfBreakdownDataLoaderService(
  private val instrumentRepository: InstrumentRepository,
  private val etfPositionRepository: EtfPositionRepository,
  private val transactionCalculationService: TransactionCalculationService,
  private val syntheticEtfCalculationService: SyntheticEtfCalculationService,
) {
  companion object {
    private val DIAGNOSTIC_PROVIDERS =
      listOf(ProviderName.LIGHTYEAR, ProviderName.FT, ProviderName.TRADING212, ProviderName.TULEVA)
    private val BREAKDOWN_PROVIDERS = DIAGNOSTIC_PROVIDERS + ProviderName.SYNTHETIC
  }

  fun loadBreakdownData(
    etfSymbols: List<String>?,
    platformFilter: Set<Platform>?,
  ): EtfBreakdownData {
    val allInstruments = instrumentRepository.findAll().toList()
    val filteredInstruments = selectInstruments(allInstruments, etfSymbols)
    val hasSynthetic = filteredInstruments.any { it.isSynthetic() }
    val transactionData =
      transactionCalculationService.batchCalculateAll((if (hasSynthetic) allInstruments else filteredInstruments).map { it.id })
    val filteredTransactionData = applyPlatformFilter(transactionData, platformFilter)
    val heldIds = filteredInstruments.filter { isHeld(it, filteredTransactionData, platformFilter) }.map { it.id }
    val positionsByEtfId = loadHoldingRows(heldIds)
    return EtfBreakdownData(
      instruments = filteredInstruments,
      positionsByEtfId = positionsByEtfId,
      transactionDataByInstrumentId = filteredTransactionData,
      allTransactionData = transactionData,
      syntheticHoldingsByEtfId =
        buildSyntheticHoldings(filteredInstruments, positionsByEtfId, platformFilter, allInstruments, transactionData),
    )
  }

  private fun selectInstruments(
    allInstruments: List<Instrument>,
    etfSymbols: List<String>?,
  ): List<Instrument> {
    val breakdownInstruments = allInstruments.filter { it.providerName in BREAKDOWN_PROVIDERS }
    return etfSymbols?.let { symbols -> breakdownInstruments.filter { it.symbol in symbols } } ?: breakdownInstruments
  }

  private fun loadHoldingRows(etfIds: List<Long>): Map<Long, List<HoldingPositionRow>> {
    if (etfIds.isEmpty()) return emptyMap()
    return etfPositionRepository
      .findLatestHoldingRowsByEtfIds(etfIds)
      .sortedByDescending { it.weightPercentage }
      .groupBy { it.etfInstrumentId }
  }

  private fun buildSyntheticHoldings(
    instruments: List<Instrument>,
    positionsByEtfId: Map<Long, List<HoldingPositionRow>>,
    platformFilter: Set<Platform>?,
    allInstruments: List<Instrument>,
    transactionData: Map<Long, InstrumentTransactionData>,
  ): Map<Long, List<InternalHoldingData>> {
    val synthetic = instruments.filter { it.isSynthetic() }
    if (synthetic.isEmpty()) return emptyMap()
    val instrumentsBySymbol = allInstruments.associateBy { it.symbol }
    return synthetic.associate { etf ->
      val positions = positionsByEtfId[etf.id].orEmpty()
      etf.id to syntheticEtfCalculationService.buildHoldings(positions, etf.symbol, platformFilter, instrumentsBySymbol, transactionData)
    }
  }

  fun loadDiagnosticData(): DiagnosticData {
    val allInstruments = instrumentRepository.findByProviderNameIn(DIAGNOSTIC_PROVIDERS)
    val instrumentIds = allInstruments.map { it.id }
    val allPositions = loadPositionsForInstruments(instrumentIds)
    val transactionData = transactionCalculationService.batchCalculateAll(instrumentIds)
    return DiagnosticData(
      instruments = allInstruments,
      positionsByEtfId = allPositions.groupBy { it.etfInstrument.id },
      transactionDataByInstrumentId = transactionData,
    )
  }

  private fun isHeld(
    instrument: Instrument,
    transactionData: Map<Long, InstrumentTransactionData>,
    platformFilter: Set<Platform>?,
  ): Boolean {
    if (instrument.isSynthetic()) return true
    return transactionData[instrument.id]?.isHeld(platformFilter) == true
  }

  private fun loadPositionsForInstruments(instrumentIds: List<Long>): List<EtfPosition> =
    instrumentIds.takeIf { it.isNotEmpty() }?.let { etfPositionRepository.findLatestPositionsByEtfIds(it) }
      ?: emptyList()

  private fun applyPlatformFilter(
    transactionData: Map<Long, InstrumentTransactionData>,
    platformFilter: Set<Platform>?,
  ): Map<Long, InstrumentTransactionData> =
    platformFilter?.let { filter ->
      transactionData.mapValues { (_, data) ->
        val filteredQuantityByPlatform = data.quantityByPlatform.filterKeys { it in filter }
        val filteredQuantity = filteredQuantityByPlatform.values.fold(BigDecimal.ZERO) { acc, qty -> acc.add(qty) }
        val filteredPlatforms = data.platforms.filter { it in filter }.toSet()
        InstrumentTransactionData(
          netQuantity = filteredQuantity,
          platforms = filteredPlatforms,
          quantityByPlatform = filteredQuantityByPlatform,
        )
      }
    } ?: transactionData
}
