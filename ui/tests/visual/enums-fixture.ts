import { API_ENDPOINTS } from '../../constants/api'
import type { EnumsResponse } from '../../models/generated/domain-models'
import { mockPlatforms } from '../fixtures'
import { apiRoute, type RouteStub } from './stub'

const ENUMS: EnumsResponse = {
  platforms: mockPlatforms,
  providers: ['BINANCE', 'FT', 'LIGHTYEAR', 'MANUAL', 'SYNTHETIC', 'TRADING212'],
  transactionTypes: ['BUY', 'SELL'],
  categories: ['CASH', 'CRYPTO', 'ETF'],
  currencies: ['AUD', 'CAD', 'CHF', 'DKK', 'EUR', 'GBP', 'HKD', 'JPY', 'NOK', 'SEK', 'SGD', 'USD'],
}

export const stubEnums: RouteStub = async page => {
  await page.route(apiRoute(API_ENDPOINTS.ENUMS), route => route.fulfill({ json: ENUMS }))
}
