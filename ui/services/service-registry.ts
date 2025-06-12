import { UtilityService } from './utility-service'
import { createCrudService } from './crud-service-factory'
import { Instrument } from '../models/instrument'
import { PortfolioTransaction } from '../models/portfolio-transaction'
import { CACHE_KEYS } from '../constants/cache-keys'

export const instrumentService = createCrudService<Instrument>(
  '/api/instruments',
  CACHE_KEYS.INSTRUMENTS
)
export const transactionService = createCrudService<PortfolioTransaction>(
  '/api/transactions',
  CACHE_KEYS.TRANSACTIONS
)

export const getUtilityService = () => new UtilityService()
