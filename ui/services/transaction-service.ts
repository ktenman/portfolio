import { PortfolioTransaction } from '../models/portfolio-transaction'
import { CACHE_KEYS } from '../constants/cache-keys'
import { BaseCrudService } from './base-crud-service'

export class TransactionService extends BaseCrudService<PortfolioTransaction> {
  constructor() {
    super('/api/transactions', CACHE_KEYS.TRANSACTIONS)
  }
}
