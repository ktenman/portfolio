/* eslint-disable no-unused-vars */
export enum ProviderName {
  ALPHA_VANTAGE = 'ALPHA_VANTAGE',
  BINANCE = 'BINANCE',
  FT = 'FT',
}
/* eslint-enable no-unused-vars */
export const PROVIDER_NAME_DISPLAY: Record<ProviderName, string> = {
  [ProviderName.ALPHA_VANTAGE]: 'Alpha Vantage',
  [ProviderName.BINANCE]: 'Binance',
  [ProviderName.FT]: 'Financial Times',
}
