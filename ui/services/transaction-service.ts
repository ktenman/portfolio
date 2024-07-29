import { ApiError } from '../models/api-error'
import { PortfolioTransaction } from '../models/portfolio-transaction'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CachePut } from '../decorators/cache-put.decorator'
import { CacheEvict } from '../decorators/cache-evict.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'

export class TransactionService {
  private readonly baseUrl = '/api/transactions'

  @CachePut(CACHE_KEYS.TRANSACTIONS)
  async saveTransaction(transaction: PortfolioTransaction): Promise<PortfolioTransaction> {
    return this.makeRequest(this.baseUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(transaction),
    })
  }

  @Cacheable(CACHE_KEYS.TRANSACTIONS)
  async getAllTransactions(): Promise<PortfolioTransaction[]> {
    return this.makeRequest(this.baseUrl)
  }

  @CacheEvict(CACHE_KEYS.TRANSACTIONS)
  async updateTransaction(
    id: number,
    transaction: PortfolioTransaction
  ): Promise<PortfolioTransaction> {
    return this.makeRequest(`${this.baseUrl}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(transaction),
    })
  }

  @CacheEvict(CACHE_KEYS.TRANSACTIONS)
  async deleteTransaction(id: number): Promise<void> {
    await this.makeRequest(`${this.baseUrl}/${id}`, {
      method: 'DELETE',
    })
  }

  private async makeRequest(url: string, options: RequestInit = {}): Promise<any> {
    const response = await fetch(url, {
      ...options,
      redirect: 'manual', // This prevents automatic redirect following
    })

    if (response.type === 'opaqueredirect' || response.status === 302) {
      // Handle redirect by forcing a full page reload
      window.location.href = response.headers.get('Location') || '/login'
      // Force reload to ensure Caddy handles the redirect
      window.location.reload()
      throw new Error('Redirecting and reloading page')
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new ApiError(
        response.status,
        errorData?.message ?? `Failed to perform operation on transaction`,
        errorData?.debugMessage ?? `HTTP error! status: ${response.status}`,
        errorData?.validationErrors ?? {}
      )
    }

    return response.json()
  }
}
