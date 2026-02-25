export const API_ENDPOINTS = {
  INSTRUMENTS: '/instruments',
  INSTRUMENTS_REFRESH_PRICES: '/instruments/refresh-prices',
  TRANSACTIONS: '/transactions',
  PORTFOLIO_SUMMARY_HISTORICAL: '/portfolio-summary/historical',
  PORTFOLIO_SUMMARY_CURRENT: '/portfolio-summary/current',
  PORTFOLIO_SUMMARY_RECALCULATE: '/portfolio-summary/recalculate',
  PORTFOLIO_SUMMARY_PREDICTIONS: '/portfolio-summary/predictions',
  ETF_BREAKDOWN: '/etf-breakdown',
  DIVERSIFICATION: '/diversification',
  ENUMS: '/enums',
  LOGOS: '/logos',
  CALCULATOR: '/calculator',
  BUILD_INFO: '/build-info',
} as const

export const REFETCH_INTERVALS = {
  INSTRUMENTS: 2000,
  DIVERSIFICATION_ETFS: 60 * 60 * 1000,
} as const
