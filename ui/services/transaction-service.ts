import { PortfolioTransaction } from '../models/portfolio-transaction'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CachePut } from '../decorators/cache-put.decorator'
import { CacheEvict } from '../decorators/cache-evict.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'
import { ApiClient } from './api-client'

export class TransactionService {
  private readonly baseUrl = '/api/transactions'

  @Cacheable(CACHE_KEYS.TRANSACTIONS)
  async getAll(): Promise<PortfolioTransaction[]> {
    return ApiClient.get<PortfolioTransaction[]>(this.baseUrl)
  }

  @CachePut(CACHE_KEYS.TRANSACTIONS)
  async create(transaction: Partial<PortfolioTransaction>): Promise<PortfolioTransaction> {
    return ApiClient.post<PortfolioTransaction>(this.baseUrl, transaction)
  }

  @CacheEvict(CACHE_KEYS.TRANSACTIONS)
  async update(
    id: string | number,
    transaction: Partial<PortfolioTransaction>
  ): Promise<PortfolioTransaction> {
    return ApiClient.put<PortfolioTransaction>(`${this.baseUrl}/${id}`, transaction)
  }

  @CacheEvict(CACHE_KEYS.TRANSACTIONS)
  async delete(id: string | number): Promise<void> {
    await ApiClient.delete(`${this.baseUrl}/${id}`)
  }
}
