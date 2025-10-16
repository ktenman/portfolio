import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TransactionTable from './transaction-table.vue'
import { TransactionType, Platform } from '../../models/generated/domain-models'
import { createTransactionDto, createInstrumentDto } from '../../tests/fixtures'

describe('TransactionTable', () => {
  const mockInstruments = [
    createInstrumentDto({
      id: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
    }),
    createInstrumentDto({
      id: 2,
      symbol: 'GOOGL',
      name: 'Alphabet Inc.',
    }),
  ]

  describe('transaction sorting', () => {
    it('should sort transactions by transaction date descending then by ID descending', () => {
      const transactions = [
        createTransactionDto({
          id: 1,
          instrumentId: 1,
          symbol: 'AAPL',
          transactionDate: '2024-07-01',
          transactionType: TransactionType.BUY,
          quantity: 10,
          price: 100,
          platform: Platform.SWEDBANK,
        }),
        createTransactionDto({
          id: 2,
          instrumentId: 1,
          symbol: 'AAPL',
          transactionDate: '2024-07-19',
          transactionType: TransactionType.SELL,
          quantity: 5,
          price: 150,
          platform: Platform.SWEDBANK,
        }),
        createTransactionDto({
          id: 3,
          instrumentId: 1,
          symbol: 'AAPL',
          transactionDate: '2024-07-15',
          transactionType: TransactionType.BUY,
          quantity: 10,
          price: 100,
          platform: Platform.SWEDBANK,
        }),
      ]

      const wrapper = mount(TransactionTable, {
        props: {
          transactions,
          instruments: mockInstruments,
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const sortedTransactions = dataTable.props('items')

      expect(sortedTransactions).toHaveLength(3)
      expect(sortedTransactions[0].id).toBe(2)
      expect(sortedTransactions[0].transactionDate).toBe('2024-07-19')
      expect(sortedTransactions[1].id).toBe(3)
      expect(sortedTransactions[1].transactionDate).toBe('2024-07-15')
      expect(sortedTransactions[2].id).toBe(1)
      expect(sortedTransactions[2].transactionDate).toBe('2024-07-01')
    })

    it('should sort by ID descending when transaction dates are the same', () => {
      const transactions = [
        createTransactionDto({
          id: 10,
          instrumentId: 1,
          symbol: 'AAPL',
          transactionDate: '2024-07-15',
          transactionType: TransactionType.BUY,
          quantity: 10,
          price: 100,
          platform: Platform.LHV,
        }),
        createTransactionDto({
          id: 25,
          instrumentId: 1,
          symbol: 'AAPL',
          transactionDate: '2024-07-15',
          transactionType: TransactionType.SELL,
          quantity: 5,
          price: 150,
          platform: Platform.LHV,
        }),
        createTransactionDto({
          id: 15,
          instrumentId: 1,
          symbol: 'AAPL',
          transactionDate: '2024-07-15',
          transactionType: TransactionType.BUY,
          quantity: 8,
          price: 120,
          platform: Platform.LHV,
        }),
      ]

      const wrapper = mount(TransactionTable, {
        props: {
          transactions,
          instruments: mockInstruments,
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const sortedTransactions = dataTable.props('items')

      expect(sortedTransactions).toHaveLength(3)
      expect(sortedTransactions[0].id).toBe(25)
      expect(sortedTransactions[1].id).toBe(15)
      expect(sortedTransactions[2].id).toBe(10)
      expect(sortedTransactions.every((t: any) => t.transactionDate === '2024-07-15')).toBe(true)
    })

    it('should handle mixed dates and IDs correctly', () => {
      const transactions = [
        createTransactionDto({
          id: 100,
          instrumentId: 2,
          symbol: 'GOOGL',
          transactionDate: '2024-01-01',
          transactionType: TransactionType.BUY,
          quantity: 10,
          price: 100,
          platform: Platform.BINANCE,
        }),
        createTransactionDto({
          id: 50,
          instrumentId: 2,
          symbol: 'GOOGL',
          transactionDate: '2024-12-31',
          transactionType: TransactionType.SELL,
          quantity: 5,
          price: 200,
          platform: Platform.BINANCE,
        }),
        createTransactionDto({
          id: 200,
          instrumentId: 2,
          symbol: 'GOOGL',
          transactionDate: '2024-01-01',
          transactionType: TransactionType.BUY,
          quantity: 15,
          price: 95,
          platform: Platform.BINANCE,
        }),
        createTransactionDto({
          id: 75,
          instrumentId: 2,
          symbol: 'GOOGL',
          transactionDate: '2024-06-15',
          transactionType: TransactionType.BUY,
          quantity: 20,
          price: 150,
          platform: Platform.BINANCE,
        }),
      ]

      const wrapper = mount(TransactionTable, {
        props: {
          transactions,
          instruments: mockInstruments,
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const sortedTransactions = dataTable.props('items')

      expect(sortedTransactions).toHaveLength(4)
      expect(sortedTransactions[0].id).toBe(50)
      expect(sortedTransactions[0].transactionDate).toBe('2024-12-31')
      expect(sortedTransactions[1].id).toBe(75)
      expect(sortedTransactions[1].transactionDate).toBe('2024-06-15')
      expect(sortedTransactions[2].id).toBe(200)
      expect(sortedTransactions[2].transactionDate).toBe('2024-01-01')
      expect(sortedTransactions[3].id).toBe(100)
      expect(sortedTransactions[3].transactionDate).toBe('2024-01-01')
    })

    it('should enrich transactions with instrument names', () => {
      const transactions = [
        createTransactionDto({
          id: 1,
          instrumentId: 1,
          symbol: 'AAPL',
          transactionDate: '2024-07-01',
          transactionType: TransactionType.BUY,
          quantity: 10,
          price: 100,
          platform: Platform.SWEDBANK,
        }),
        createTransactionDto({
          id: 2,
          instrumentId: 2,
          symbol: 'GOOGL',
          transactionDate: '2024-07-02',
          transactionType: TransactionType.BUY,
          quantity: 5,
          price: 200,
          platform: Platform.SWEDBANK,
        }),
      ]

      const wrapper = mount(TransactionTable, {
        props: {
          transactions,
          instruments: mockInstruments,
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const enrichedTransactions = dataTable.props('items')

      expect(enrichedTransactions[0].instrumentName).toBe('Alphabet Inc.')
      expect(enrichedTransactions[1].instrumentName).toBe('Apple Inc.')
    })

    it('should handle empty transaction list', () => {
      const wrapper = mount(TransactionTable, {
        props: {
          transactions: [],
          instruments: mockInstruments,
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const sortedTransactions = dataTable.props('items')

      expect(sortedTransactions).toHaveLength(0)
    })

    it('should handle transactions with missing IDs gracefully', () => {
      const transactions = [
        createTransactionDto({
          id: undefined,
          instrumentId: 1,
          symbol: 'AAPL',
          transactionDate: '2024-07-01',
          transactionType: TransactionType.BUY,
          quantity: 10,
          price: 100,
          platform: Platform.SWEDBANK,
        }),
        createTransactionDto({
          id: 1,
          instrumentId: 1,
          symbol: 'AAPL',
          transactionDate: '2024-07-01',
          transactionType: TransactionType.SELL,
          quantity: 5,
          price: 150,
          platform: Platform.SWEDBANK,
        }),
      ]

      const wrapper = mount(TransactionTable, {
        props: {
          transactions,
          instruments: mockInstruments,
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const sortedTransactions = dataTable.props('items')

      expect(sortedTransactions).toHaveLength(2)
      expect(sortedTransactions[0].id).toBe(1)
      expect(sortedTransactions[1].id).toBeUndefined()
    })
  })

  describe('component integration', () => {
    it('should pass correct props to DataTable component', () => {
      const transactions = [
        createTransactionDto({
          id: 1,
          instrumentId: 1,
          symbol: 'AAPL',
          transactionDate: '2024-07-01',
          transactionType: TransactionType.BUY,
          quantity: 10,
          price: 100,
          platform: Platform.SWEDBANK,
        }),
      ]

      const wrapper = mount(TransactionTable, {
        props: {
          transactions,
          instruments: mockInstruments,
          isLoading: true,
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })

      expect(dataTable.props('isLoading')).toBe(true)
      expect(dataTable.props('emptyMessage')).toBe(
        'No transactions found. Add a new transaction to get started.'
      )
      expect(dataTable.props('columns')).toBeDefined()
    })

    it('should handle unknown instruments gracefully', () => {
      const transactions = [
        createTransactionDto({
          id: 1,
          instrumentId: 999,
          symbol: 'UNKNOWN',
          transactionDate: '2024-07-01',
          transactionType: TransactionType.BUY,
          quantity: 10,
          price: 100,
          platform: Platform.SWEDBANK,
        }),
      ]

      const wrapper = mount(TransactionTable, {
        props: {
          transactions,
          instruments: mockInstruments,
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const enrichedTransactions = dataTable.props('items')

      expect(enrichedTransactions[0].instrumentName).toBe('Unknown')
    })
  })
})
