import { createCrudService } from './crud-service-factory'
import { PortfolioTransaction } from '../models/portfolio-transaction'
import { CACHE_KEYS } from '../constants/cache-keys'

export const transactionService = createCrudService<PortfolioTransaction>(
  '/api/transactions',
  CACHE_KEYS.TRANSACTIONS
)
