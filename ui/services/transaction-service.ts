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

  // Alias for CRUD interface compatibility
  async create(transaction: Partial<PortfolioTransaction>): Promise<PortfolioTransaction> {
    return this.saveTransaction(transaction as PortfolioTransaction)
  }

  @Cacheable(CACHE_KEYS.TRANSACTIONS)
  async getAllTransactions(): Promise<PortfolioTransaction[]> {
    return ApiClient.get<PortfolioTransaction[]>(this.baseUrl)
  }

  // Alias for CRUD interface compatibility
  async getAll(): Promise<PortfolioTransaction[]> {
    return this.getAllTransactions()
  }

  @CacheEvict(CACHE_KEYS.TRANSACTIONS)
  async updateTransaction(
    id: number,
    transaction: PortfolioTransaction
  ): Promise<PortfolioTransaction> {
    return ApiClient.put<PortfolioTransaction>(`${this.baseUrl}/${id}`, transaction)
  }

  // Alias for CRUD interface compatibility
  async update(
    id: string | number,
    transaction: Partial<PortfolioTransaction>
  ): Promise<PortfolioTransaction> {
    return this.updateTransaction(Number(id), transaction as PortfolioTransaction)
  }

  @CacheEvict(CACHE_KEYS.TRANSACTIONS)
  async deleteTransaction(id: number): Promise<void> {
    await ApiClient.delete(`${this.baseUrl}/${id}`)
  }

  // Alias for CRUD interface compatibility
  async delete(id: string | number): Promise<void> {
    return this.deleteTransaction(Number(id))
  }
}
