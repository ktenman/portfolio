import { ApiError } from '../models/api-error'
import { PortfolioTransaction } from '../models/portfolio-transaction'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CachePut } from '../decorators/cache-put.decorator'
import { CacheEvict } from '../decorators/cache-evict.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'

export class PortfolioTransactionService {
  private readonly baseUrl = '/api/transactions'

  @CachePut(CACHE_KEYS.TRANSACTIONS)
  async saveTransaction(transaction: PortfolioTransaction): Promise<PortfolioTransaction> {
    const response = await fetch(this.baseUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(transaction),
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new ApiError(
        response.status,
        errorData?.message ?? 'Failed to save transaction',
        errorData?.debugMessage ?? `HTTP error! status: ${response.status}`,
        errorData?.validationErrors ?? {}
      )
    }

    return response.json()
  }

  @Cacheable(CACHE_KEYS.TRANSACTIONS)
  async getAllTransactions(): Promise<PortfolioTransaction[]> {
    const response = await fetch(this.baseUrl)

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new ApiError(
        response.status,
        errorData?.message ?? 'Failed to fetch transactions',
        errorData?.debugMessage ?? `HTTP error! status: ${response.status}`,
        errorData?.validationErrors ?? {}
      )
    }

    return response.json()
  }

  @CacheEvict(CACHE_KEYS.TRANSACTIONS)
  async updateTransaction(
    id: number,
    transaction: PortfolioTransaction
  ): Promise<PortfolioTransaction> {
    const response = await fetch(`${this.baseUrl}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(transaction),
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new ApiError(
        response.status,
        errorData?.message ?? 'Failed to update transaction',
        errorData?.debugMessage ?? `HTTP error! status: ${response.status}`,
        errorData?.validationErrors ?? {}
      )
    }

    return response.json()
  }

  @CacheEvict(CACHE_KEYS.TRANSACTIONS)
  async deleteTransaction(id: number): Promise<void> {
    const response = await fetch(`${this.baseUrl}/${id}`, {
      method: 'DELETE',
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new ApiError(
        response.status,
        errorData?.message ?? 'Failed to delete transaction',
        errorData?.debugMessage ?? `HTTP error! status: ${response.status}`,
        errorData?.validationErrors ?? {}
      )
    }
  }
}
