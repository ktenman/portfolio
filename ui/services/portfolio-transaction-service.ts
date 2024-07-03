import { ApiError } from '../models/api-error'
import { PortfolioTransaction } from '../models/portfolio-transaction'

export class PortfolioTransactionService {
  private readonly baseUrl = '/api/transactions'

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

  async getTransaction(id: number): Promise<PortfolioTransaction> {
    const response = await fetch(`${this.baseUrl}/${id}`)

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new ApiError(
        response.status,
        errorData?.message ?? 'Failed to fetch transaction',
        errorData?.debugMessage ?? `HTTP error! status: ${response.status}`,
        errorData?.validationErrors ?? {}
      )
    }

    return response.json()
  }
}
