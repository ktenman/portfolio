import { describe, it, expect, vi, beforeEach } from 'vitest'
import { transactionsService } from './transactions-service'
import { httpClient } from '../utils/http-client'
import { TransactionType, Platform } from '../models/generated/domain-models'
import { createTransactionDto } from '../tests/fixtures'

vi.mock('../utils/http-client', () => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('transactionsService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getAll', () => {
    it('should fetch all transactions', async () => {
      const mockTransactions = [
        createTransactionDto({
          id: 1,
          instrumentId: 1,
          symbol: 'AAPL',
          transactionDate: '2023-01-15',
          transactionType: TransactionType.BUY,
          quantity: 10,
          price: 150.5,
          platform: Platform.BINANCE,
          currency: 'EUR',
        }),
        createTransactionDto({
          id: 2,
          instrumentId: 2,
          symbol: 'BTC',
          transactionDate: '2023-02-20',
          transactionType: TransactionType.SELL,
          quantity: 5,
          price: 45000,
          platform: Platform.COINBASE,
          currency: 'EUR',
        }),
      ]
      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockTransactions,
      })

      const result = await transactionsService.getAll()

      expect(httpClient.get).toHaveBeenCalledWith('/transactions', { params: {} })
      expect(result).toEqual(mockTransactions)
    })

    it('should handle empty transaction list', async () => {
      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: [],
      })

      const result = await transactionsService.getAll()

      expect(httpClient.get).toHaveBeenCalledWith('/transactions', { params: {} })
      expect(result).toEqual([])
    })

    it('should propagate errors on getAll', async () => {
      const error = new Error('Server error')
      vi.mocked(httpClient.get).mockRejectedValueOnce(error)

      await expect(transactionsService.getAll()).rejects.toThrow('Server error')
    })

    it('should fetch transactions with platform filter', async () => {
      const mockTransactions = [
        createTransactionDto({
          id: 1,
          platform: Platform.BINANCE,
        }),
      ]
      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockTransactions,
      })

      const result = await transactionsService.getAll(['BINANCE', 'COINBASE'])

      expect(httpClient.get).toHaveBeenCalledWith('/transactions', {
        params: { platforms: 'BINANCE,COINBASE' },
      })
      expect(result).toEqual(mockTransactions)
    })

    it('should handle empty platform filter as no filter', async () => {
      const mockTransactions = [createTransactionDto({ id: 1 })]
      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockTransactions,
      })

      const result = await transactionsService.getAll([])

      expect(httpClient.get).toHaveBeenCalledWith('/transactions', { params: {} })
      expect(result).toEqual(mockTransactions)
    })
  })

  describe('create', () => {
    it('should create a new transaction', async () => {
      const newTransaction = {
        instrumentId: 3,
        symbol: 'GOOGL',
        transactionDate: '2023-03-10',
        transactionType: TransactionType.BUY,
        quantity: 20,
        price: 250.75,
        platform: Platform.AVIVA,
      }

      const createdTransaction = createTransactionDto({
        id: 3,
        ...newTransaction,
      })

      vi.mocked(httpClient.post).mockResolvedValueOnce({
        data: createdTransaction,
      })

      const result = await transactionsService.create(newTransaction)

      expect(httpClient.post).toHaveBeenCalledWith('/transactions', newTransaction)
      expect(result).toEqual(createdTransaction)
    })

    it('should handle partial transaction data', async () => {
      const partialTransaction = {
        instrumentId: 4,
        symbol: 'ETH',
        transactionType: TransactionType.SELL,
        quantity: 15,
        price: 2000,
        transactionDate: '2023-03-15',
        platform: Platform.BINANCE,
      }

      const createdTransaction = createTransactionDto({
        id: 4,
        ...partialTransaction,
      })

      vi.mocked(httpClient.post).mockResolvedValueOnce({
        data: createdTransaction,
      })

      const result = await transactionsService.create(partialTransaction)

      expect(httpClient.post).toHaveBeenCalledWith('/transactions', partialTransaction)
      expect(result).toEqual(createdTransaction)
    })

    it('should propagate errors on create', async () => {
      const error = new Error('Invalid transaction data')
      vi.mocked(httpClient.post).mockRejectedValueOnce(error)

      await expect(transactionsService.create({ quantity: -1 })).rejects.toThrow(
        'Invalid transaction data'
      )
    })
  })

  describe('update', () => {
    it('should update a transaction by numeric id', async () => {
      const updateData = {
        quantity: 25,
        price: 155.5,
      }

      const updatedTransaction = createTransactionDto({
        id: 1,
        instrumentId: 1,
        symbol: 'AAPL',
        transactionDate: '2023-01-15',
        transactionType: TransactionType.BUY,
        quantity: 25,
        price: 155.5,
        platform: Platform.BINANCE,
        currency: 'EUR',
      })

      vi.mocked(httpClient.put).mockResolvedValueOnce({
        data: updatedTransaction,
      })

      const result = await transactionsService.update(1, updateData)

      expect(httpClient.put).toHaveBeenCalledWith('/transactions/1', updateData)
      expect(result).toEqual(updatedTransaction)
    })

    it('should update a transaction by string id', async () => {
      const updateData = {
        transactionDate: '2023-01-20',
      }

      const updatedTransaction = createTransactionDto({
        id: 2,
        instrumentId: 2,
        symbol: 'BTC',
        transactionDate: '2023-01-20',
        transactionType: TransactionType.SELL,
        quantity: 5,
        price: 45000,
        platform: Platform.COINBASE,
        currency: 'EUR',
      })

      vi.mocked(httpClient.put).mockResolvedValueOnce({
        data: updatedTransaction,
      })

      const result = await transactionsService.update('2', updateData)

      expect(httpClient.put).toHaveBeenCalledWith('/transactions/2', updateData)
      expect(result).toEqual(updatedTransaction)
    })

    it('should handle full transaction update', async () => {
      const fullUpdate = {
        instrumentId: 5,
        symbol: 'MSFT',
        transactionDate: '2023-04-01',
        transactionType: TransactionType.BUY,
        quantity: 100,
        price: 50.25,
        platform: Platform.AVIVA,
      }

      const updatedTransaction = createTransactionDto({
        id: 5,
        ...fullUpdate,
      })

      vi.mocked(httpClient.put).mockResolvedValueOnce({
        data: updatedTransaction,
      })

      const result = await transactionsService.update(5, fullUpdate)

      expect(httpClient.put).toHaveBeenCalledWith('/transactions/5', fullUpdate)
      expect(result).toEqual(updatedTransaction)
    })

    it('should propagate errors on update', async () => {
      const error = new Error('Transaction not found')
      vi.mocked(httpClient.put).mockRejectedValueOnce(error)

      await expect(transactionsService.update(999, { quantity: 10 })).rejects.toThrow(
        'Transaction not found'
      )
    })
  })

  describe('delete', () => {
    it('should delete a transaction by numeric id', async () => {
      vi.mocked(httpClient.delete).mockResolvedValueOnce({
        data: undefined,
      })

      const result = await transactionsService.delete(1)

      expect(httpClient.delete).toHaveBeenCalledWith('/transactions/1')
      expect(result).toBeUndefined()
    })

    it('should delete a transaction by string id', async () => {
      vi.mocked(httpClient.delete).mockResolvedValueOnce({
        data: undefined,
      })

      const result = await transactionsService.delete('2')

      expect(httpClient.delete).toHaveBeenCalledWith('/transactions/2')
      expect(result).toBeUndefined()
    })

    it('should handle void response correctly', async () => {
      vi.mocked(httpClient.delete).mockResolvedValueOnce({
        status: 204,
        data: null,
      })

      const result = await transactionsService.delete(3)

      expect(httpClient.delete).toHaveBeenCalledWith('/transactions/3')
      expect(result).toBeUndefined()
    })

    it('should propagate errors on delete', async () => {
      const error = new Error('Cannot delete transaction')
      vi.mocked(httpClient.delete).mockRejectedValueOnce(error)

      await expect(transactionsService.delete(999)).rejects.toThrow('Cannot delete transaction')
    })
  })
})
