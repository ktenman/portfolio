import { PortfolioTransaction } from '../models/portfolio-transaction'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CachePut } from '../decorators/cache-put.decorator'
import { CacheEvict } from '../decorators/cache-evict.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'
import { ApiClient } from './api-client.ts'

export class TransactionService {
  private readonly baseUrl = '/api/transactions'

  @CachePut(CACHE_KEYS.TRANSACTIONS)
  async saveTransaction(transaction: PortfolioTransaction): Promise<PortfolioTransaction> {
    return ApiClient.post<PortfolioTransaction>(this.baseUrl, transaction)
  }

  @Cacheable(CACHE_KEYS.TRANSACTIONS)
  async getAllTransactions(): Promise<PortfolioTransaction[]> {
    return ApiClient.get<PortfolioTransaction[]>(this.baseUrl)
  }

  @CacheEvict(CACHE_KEYS.TRANSACTIONS)
  async updateTransaction(
    id: number,
    transaction: PortfolioTransaction
  ): Promise<PortfolioTransaction> {
    return ApiClient.put<PortfolioTransaction>(`${this.baseUrl}/${id}`, transaction)
  }

  @CacheEvict(CACHE_KEYS.TRANSACTIONS)
  async deleteTransaction(id: number): Promise<void> {
    await ApiClient.delete(`${this.baseUrl}/${id}`)
  }
}
