package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.service.transaction.InstrumentTransactionData

data class DiagnosticData(
  val instruments: List<Instrument>,
  val positionsByEtfId: Map<Long, List<EtfPosition>>,
  val transactionDataByInstrumentId: Map<Long, InstrumentTransactionData>,
)
