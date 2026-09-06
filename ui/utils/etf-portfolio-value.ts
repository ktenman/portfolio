import type { EtfDetailDto, InstrumentDto } from '../models/generated/domain-models'

export const etfPortfolioValues = (
  etfs: EtfDetailDto[],
  instruments: InstrumentDto[]
): Map<number, number> => {
  const valueById = new Map(instruments.map(i => [i.id, i.currentValue ?? 0]))
  const valueBySymbol = new Map(instruments.map(i => [i.symbol, i.currentValue ?? 0]))
  return new Map(
    etfs.map(etf => [
      etf.instrumentId,
      etf.constituentSymbols.length === 0
        ? (valueById.get(etf.instrumentId) ?? 0)
        : etf.constituentSymbols.reduce((sum, s) => sum + (valueBySymbol.get(s) ?? 0), 0),
    ])
  )
}
