export const formatPlatformName = (platform: string): string => {
  const platformMap: Record<string, string> = {
    TRADING212: 'Trading 212',
    LIGHTYEAR: 'Lightyear',
    SWEDBANK: 'Swedbank',
    BINANCE: 'Binance',
    COINBASE: 'Coinbase',
    LHV: 'LHV',
    AVIVA: 'Aviva',
    IBKR: 'IBKR',
    UNKNOWN: 'Unknown',
  }

  return platformMap[platform] || platform
}
