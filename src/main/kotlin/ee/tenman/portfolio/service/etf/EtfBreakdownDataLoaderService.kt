package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
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
) {
  fun loadBreakdownData(
    etfSymbols: List<String>?,
    platformFilter: Set<Platform>?,
  ): EtfBreakdownData {
    val providers = listOf(ProviderName.LIGHTYEAR, ProviderName.FT, ProviderName.SYNTHETIC)
    val allInstruments = instrumentRepository.findByProviderNameIn(providers)
    val filteredInstruments =
      etfSymbols?.let { symbols -> allInstruments.filter { it.symbol in symbols } } ?: allInstruments
    val instrumentIds = filteredInstruments.map { it.id }
    val allPositions = loadPositionsForInstruments(instrumentIds)
    val transactionData = transactionCalculationService.batchCalculateAll(instrumentIds)
    val filteredTransactionData = applyPlatformFilter(transactionData, platformFilter)
    return EtfBreakdownData(
      instruments = filteredInstruments,
      positionsByEtfId = allPositions.groupBy { it.etfInstrument.id },
      transactionDataByInstrumentId = filteredTransactionData,
      allTransactionData = transactionData,
    )
  }

  fun loadDiagnosticData(): DiagnosticData {
    val providers = listOf(ProviderName.LIGHTYEAR, ProviderName.FT)
    val allInstruments = instrumentRepository.findByProviderNameIn(providers)
    val instrumentIds = allInstruments.map { it.id }
    val allPositions = loadPositionsForInstruments(instrumentIds)
    val transactionData = transactionCalculationService.batchCalculateAll(instrumentIds)
    return DiagnosticData(
      instruments = allInstruments,
      positionsByEtfId = allPositions.groupBy { it.etfInstrument.id },
      transactionDataByInstrumentId = transactionData,
    )
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
