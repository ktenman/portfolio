package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.specification.InstrumentSpecifications
import ee.tenman.portfolio.repository.specification.InstrumentSpecifications.ETF_PROVIDERS
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
    val filteredTransactionData =
      platformFilter?.let {
      transactionCalculationService.batchCalculateAll(instrumentIds, it)
    } ?: allTransactionData
    return EtfBreakdownData(
      instruments = instruments,
      positionsByEtfId = allPositions.groupBy { it.etfInstrument.id },
      transactionDataByInstrumentId = filteredTransactionData,
      allTransactionData = allTransactionData,
    )
  }

  fun loadDiagnosticData(): DiagnosticData {
    val allInstruments = instrumentRepository.findByProviderNameIn(listOf(ProviderName.LIGHTYEAR, ProviderName.FT))
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
    val spec = InstrumentSpecifications.hasProviderNameIn(ETF_PROVIDERS)
    val filtered =
      etfSymbols
        ?.takeIf { it.isNotEmpty() }
      ?.let { spec.and(InstrumentSpecifications.hasSymbolIn(it)) }
      ?: spec
    return instrumentRepository.findAll(filtered)
  }

  private fun loadPositionsForInstruments(instrumentIds: List<Long>): List<EtfPosition> =
    instrumentIds.takeIf { it.isNotEmpty() }?.let { etfPositionRepository.findLatestPositionsByEtfIds(it) }
      ?: emptyList()
}
