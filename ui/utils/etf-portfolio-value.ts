import type { EtfDetailDto, InstrumentDto } from '../models/generated/domain-models'

export const etfPortfolioValues = (
  etfs: EtfDetailDto[],
  instruments: InstrumentDto[]
): Map<number, number> => {
  const valueById = new Map(instruments.map(i => [i.id, i.currentValue ?? 0]))
  return new Map(etfs.map(etf => [etf.instrumentId, valueById.get(etf.instrumentId) ?? 0]))
}
