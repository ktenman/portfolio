import { API_ENDPOINTS } from '../../constants/api'
import type { EnumsResponse } from '../../models/generated/domain-models'
import { apiRoute, type RouteStub } from './stub'

const ENUMS: EnumsResponse = {
  platforms: [
    { name: 'AVIVA', displayName: 'Aviva' },
    { name: 'BINANCE', displayName: 'Binance' },
    { name: 'COINBASE', displayName: 'Coinbase' },
    { name: 'IBKR', displayName: 'IBKR' },
    { name: 'LHV', displayName: 'LHV' },
    { name: 'LIGHTYEAR', displayName: 'Lightyear' },
    { name: 'LIGHTYEAR_BUSINESS', displayName: 'Lightyear Business' },
    { name: 'SWEDBANK', displayName: 'Swedbank' },
    { name: 'TRADING212', displayName: 'Trading 212' },
    { name: 'UNKNOWN', displayName: 'Unknown' },
  ],
  providers: ['BINANCE', 'FT', 'LIGHTYEAR', 'MANUAL', 'SYNTHETIC', 'TRADING212'],
  transactionTypes: ['BUY', 'SELL'],
  categories: ['CASH', 'CRYPTO', 'ETF'],
  currencies: ['AUD', 'CAD', 'CHF', 'DKK', 'EUR', 'GBP', 'HKD', 'JPY', 'NOK', 'SEK', 'SGD', 'USD'],
}

export const stubEnums: RouteStub = async page => {
  await page.route(apiRoute(API_ENDPOINTS.ENUMS), route => route.fulfill({ json: ENUMS }))
}
