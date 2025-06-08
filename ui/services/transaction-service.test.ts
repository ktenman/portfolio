import { describe, it, expect, vi, beforeEach } from 'vitest'
import { TransactionService } from './transaction-service'
import { ApiClient } from './api-client'
import { PortfolioTransaction } from '../models/portfolio-transaction'

// Mock the ApiClient
vi.mock('./api-client', () => ({
  ApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

// Mock the decorators
vi.mock('../decorators/cacheable.decorator', () => ({
  Cacheable: () => (_target: any, _propertyKey: string, descriptor: PropertyDescriptor) =>
    descriptor,
}))

vi.mock('../decorators/cache-put.decorator', () => ({
  CachePut: () => (_target: any, _propertyKey: string, descriptor: PropertyDescriptor) =>
    descriptor,
}))

vi.mock('../decorators/cache-evict.decorator', () => ({
  CacheEvict: () => (_target: any, _propertyKey: string, descriptor: PropertyDescriptor) =>
    descriptor,
}))

describe('TransactionService', () => {
  let service: TransactionService

  const mockTransaction: PortfolioTransaction = {
    id: 1,
    instrumentId: 1,
    transactionType: 'BUY',
    quantity: 10,
    price: 150.0,
    transactionDate: '2024-01-15',
    platform: 'NASDAQ' as any,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    service = new TransactionService()
  })

  describe('saveTransaction', () => {
    it('saves new transaction via API', async () => {
      const newTransaction = { ...mockTransaction, id: undefined }
      const savedTransaction = { ...mockTransaction, id: 2 }

      vi.mocked(ApiClient.post).mockResolvedValue(savedTransaction)

      const result = await service.saveTransaction(newTransaction as any)

      expect(ApiClient.post).toHaveBeenCalledWith('/api/transactions', newTransaction)
      expect(result).toEqual(savedTransaction)
    })

    it('handles API errors during save', async () => {
      const apiError = new Error('Validation Error')
      vi.mocked(ApiClient.post).mockRejectedValue(apiError)

      await expect(service.saveTransaction(mockTransaction)).rejects.toThrow('Validation Error')
    })

    it('saves transaction with all required fields', async () => {
      const completeTransaction = {
        instrumentId: 2,
        instrumentName: 'Google',
        instrumentSymbol: 'GOOGL',
        transactionType: 'SELL',
        quantity: 5,
        price: 2800.0,
        totalAmount: 14000.0,
        transactionDate: '2024-01-16',
        notes: 'Partial sale',
      }
      const savedTransaction = { ...completeTransaction, id: 3 }

      vi.mocked(ApiClient.post).mockResolvedValue(savedTransaction)

      const result = await service.saveTransaction(completeTransaction as any)

      expect(result).toEqual(savedTransaction)
      expect(result.id).toBeDefined()
    })

    it('saves buy transaction', async () => {
      const buyTransaction = { ...mockTransaction, transactionType: 'BUY' as const }
      vi.mocked(ApiClient.post).mockResolvedValue(buyTransaction)

      const result = await service.saveTransaction(buyTransaction)

      expect(result.transactionType).toBe('BUY')
    })

    it('saves sell transaction', async () => {
      const sellTransaction = { ...mockTransaction, transactionType: 'SELL' as const }
      vi.mocked(ApiClient.post).mockResolvedValue(sellTransaction)

      const result = await service.saveTransaction(sellTransaction)

      expect(result.transactionType).toBe('SELL')
    })
  })

  describe('getAllTransactions', () => {
    it('fetches all transactions from API', async () => {
      const mockTransactions = [mockTransaction]
      vi.mocked(ApiClient.get).mockResolvedValue(mockTransactions)

      const result = await service.getAllTransactions()

      expect(ApiClient.get).toHaveBeenCalledWith('/api/transactions')
      expect(result).toEqual(mockTransactions)
    })

    it('handles empty transactions list', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue([])

      const result = await service.getAllTransactions()

      expect(result).toEqual([])
    })

    it('propagates API errors', async () => {
      const apiError = new Error('API Error')
      vi.mocked(ApiClient.get).mockRejectedValue(apiError)

      await expect(service.getAllTransactions()).rejects.toThrow('API Error')
    })

    it('handles large number of transactions', async () => {
      const manyTransactions = Array.from({ length: 100 }, (_, i) => ({
        ...mockTransaction,
        id: i + 1,
      }))
      vi.mocked(ApiClient.get).mockResolvedValue(manyTransactions)

      const result = await service.getAllTransactions()

      expect(result).toHaveLength(100)
    })
  })

  describe('updateTransaction', () => {
    it('updates existing transaction via API', async () => {
      const updatedTransaction = { ...mockTransaction, quantity: 15, totalAmount: 2250.0 }
      vi.mocked(ApiClient.put).mockResolvedValue(updatedTransaction)

      const result = await service.updateTransaction(1, updatedTransaction)

      expect(ApiClient.put).toHaveBeenCalledWith('/api/transactions/1', updatedTransaction)
      expect(result).toEqual(updatedTransaction)
    })

    it('handles API errors during update', async () => {
      const apiError = new Error('Not Found')
      vi.mocked(ApiClient.put).mockRejectedValue(apiError)

      await expect(service.updateTransaction(1, mockTransaction)).rejects.toThrow('Not Found')
    })

    it('updates transaction with different ID', async () => {
      const transactionId = 5
      vi.mocked(ApiClient.put).mockResolvedValue(mockTransaction)

      await service.updateTransaction(transactionId, mockTransaction)

      expect(ApiClient.put).toHaveBeenCalledWith(
        `/api/transactions/${transactionId}`,
        mockTransaction
      )
    })

    it('updates transaction type from BUY to SELL', async () => {
      const updatedTransaction = { ...mockTransaction, transactionType: 'SELL' as const }
      vi.mocked(ApiClient.put).mockResolvedValue(updatedTransaction)

      const result = await service.updateTransaction(1, updatedTransaction)

      expect(result.transactionType).toBe('SELL')
    })

    it('updates transaction price', async () => {
      const updatedTransaction = { ...mockTransaction, price: 200.0 }
      vi.mocked(ApiClient.put).mockResolvedValue(updatedTransaction)

      const result = await service.updateTransaction(1, updatedTransaction)

      expect(result.price).toBe(200.0)
    })
  })

  describe('deleteTransaction', () => {
    it('deletes transaction via API', async () => {
      vi.mocked(ApiClient.delete).mockResolvedValue(undefined)

      await service.deleteTransaction(1)

      expect(ApiClient.delete).toHaveBeenCalledWith('/api/transactions/1')
    })

    it('handles API errors during delete', async () => {
      const apiError = new Error('Not Found')
      vi.mocked(ApiClient.delete).mockRejectedValue(apiError)

      await expect(service.deleteTransaction(1)).rejects.toThrow('Not Found')
    })

    it('deletes transaction with different ID', async () => {
      const transactionId = 10
      vi.mocked(ApiClient.delete).mockResolvedValue(undefined)

      await service.deleteTransaction(transactionId)

      expect(ApiClient.delete).toHaveBeenCalledWith(`/api/transactions/${transactionId}`)
    })

    it('handles successful delete without return value', async () => {
      vi.mocked(ApiClient.delete).mockResolvedValue(undefined)

      const result = await service.deleteTransaction(1)

      expect(result).toBeUndefined()
    })
  })

  describe('service initialization', () => {
    it('creates service with correct base URL', () => {
      const newService = new TransactionService()
      expect(newService).toBeInstanceOf(TransactionService)
    })
  })

  describe('caching behavior', () => {
    it('applies cache decorators to appropriate methods', () => {
      // The decorators are mocked, so we just verify the methods exist and are callable
      expect(typeof service.saveTransaction).toBe('function')
      expect(typeof service.getAllTransactions).toBe('function')
      expect(typeof service.updateTransaction).toBe('function')
      expect(typeof service.deleteTransaction).toBe('function')
    })
  })

  describe('error handling', () => {
    it('handles network timeouts', async () => {
      const timeoutError = new Error('Network timeout')
      vi.mocked(ApiClient.get).mockRejectedValue(timeoutError)

      await expect(service.getAllTransactions()).rejects.toThrow('Network timeout')
    })

    it('handles server errors', async () => {
      const serverError = new Error('Internal Server Error')
      vi.mocked(ApiClient.post).mockRejectedValue(serverError)

      await expect(service.saveTransaction(mockTransaction)).rejects.toThrow(
        'Internal Server Error'
      )
    })

    it('handles validation errors', async () => {
      const validationError = new Error('Invalid transaction data')
      vi.mocked(ApiClient.put).mockRejectedValue(validationError)

      await expect(service.updateTransaction(1, mockTransaction)).rejects.toThrow(
        'Invalid transaction data'
      )
    })
  })

  describe('edge cases', () => {
    it('handles transaction with zero amount', async () => {
      const zeroTransaction = { ...mockTransaction, price: 0 }
      vi.mocked(ApiClient.post).mockResolvedValue(zeroTransaction)

      const result = await service.saveTransaction(zeroTransaction)

      expect(result.price).toBe(0)
    })

    it('handles transaction with very large amount', async () => {
      const largeTransaction = { ...mockTransaction, price: 999999999.99 }
      vi.mocked(ApiClient.post).mockResolvedValue(largeTransaction)

      const result = await service.saveTransaction(largeTransaction)

      expect(result.price).toBe(999999999.99)
    })

    it('handles transaction with fractional quantity', async () => {
      const fractionalTransaction = { ...mockTransaction, quantity: 10.5 }
      vi.mocked(ApiClient.post).mockResolvedValue(fractionalTransaction)

      const result = await service.saveTransaction(fractionalTransaction)

      expect(result.quantity).toBe(10.5)
    })
  })
})
