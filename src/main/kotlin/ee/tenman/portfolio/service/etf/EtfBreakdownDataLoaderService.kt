package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.specification.InstrumentSpecifications
import ee.tenman.portfolio.service.transaction.TransactionCalculationService
import org.springframework.stereotype.Service

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
    val instruments = findInstruments(etfSymbols)
    val instrumentIds = instruments.map { it.id }
    val allPositions = loadPositionsForInstruments(instrumentIds)
    val allTransactionData = transactionCalculationService.batchCalculateAll(instrumentIds)
    val filteredTransactionData = transactionCalculationService.batchCalculateAll(instrumentIds, platformFilter)
    return EtfBreakdownData(
      instruments = instruments,
      positionsByEtfId = allPositions.groupBy { it.etfInstrument.id },
      transactionDataByInstrumentId = filteredTransactionData,
      allTransactionData = allTransactionData,
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

  private fun findInstruments(etfSymbols: List<String>?): List<Instrument> {
    var spec = InstrumentSpecifications.hasProviderNameIn(ETF_PROVIDERS)
    if (!etfSymbols.isNullOrEmpty()) {
      spec = spec.and(InstrumentSpecifications.hasSymbolIn(etfSymbols))
    }
    return instrumentRepository.findAll(spec)
  }

  private fun loadPositionsForInstruments(instrumentIds: List<Long>): List<EtfPosition> =
    instrumentIds.takeIf { it.isNotEmpty() }?.let { etfPositionRepository.findLatestPositionsByEtfIds(it) }
      ?: emptyList()

  companion object {
    private val ETF_PROVIDERS = listOf(ProviderName.LIGHTYEAR, ProviderName.FT, ProviderName.SYNTHETIC)
  }
}
