package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.model.holding.HoldingPositionRow
import ee.tenman.portfolio.model.holding.InternalHoldingData
import ee.tenman.portfolio.service.transaction.InstrumentTransactionData

data class EtfBreakdownData(
  val instruments: List<Instrument>,
  val positionsByEtfId: Map<Long, List<HoldingPositionRow>>,
  val transactionDataByInstrumentId: Map<Long, InstrumentTransactionData>,
  val allTransactionData: Map<Long, InstrumentTransactionData>,
  val syntheticHoldingsByEtfId: Map<Long, List<InternalHoldingData>>,
)
