import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TransactionTable from './transaction-table.vue'
import { TransactionType, Platform } from '../../models/generated/domain-models'
import { createTransactionDto } from '../../tests/fixtures'

describe('TransactionTable', () => {
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
          name: 'Apple Inc.',
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
          name: 'Alphabet Inc.',
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
          name: 'Unknown',
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
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const enrichedTransactions = dataTable.props('items')

      expect(enrichedTransactions[0].instrumentName).toBe('Unknown')
    })
  })

  describe('interactive column sorting', () => {
    const transactions = [
      createTransactionDto({
        id: 1,
        name: 'Zalando SE',
        transactionDate: '2024-07-01',
        quantity: 3,
        price: 100,
      }),
      createTransactionDto({
        id: 2,
        name: 'Ångpanneföreningen AB',
        transactionDate: '2024-07-19',
        quantity: 1,
        price: 50,
      }),
      createTransactionDto({
        id: 3,
        name: 'Apple Inc',
        transactionDate: '2024-07-15',
        quantity: 2,
        price: 900,
      }),
    ]

    const mountAndSort = async (label: string, clicks: number) => {
      const wrapper = mount(TransactionTable, { props: { transactions } })
      const header = wrapper.findAll('th.sortable').find(th => th.text().startsWith(label))
      if (!header) throw new Error(`no sortable header labelled ${label}`)
      for (let i = 0; i < clicks; i++) {
        await header.trigger('click')
      }
      return wrapper.findComponent({ name: 'DataTable' }).props('items')
    }

    it('should sort by quantity ascending when the quantity header is clicked once', async () => {
      const items = await mountAndSort('Quantity', 1)

      expect(items.map((item: { quantity: number }) => item.quantity)).toEqual([1, 2, 3])
    })

    it('should sort by quantity descending when the quantity header is clicked twice', async () => {
      const items = await mountAndSort('Quantity', 2)

      expect(items.map((item: { quantity: number }) => item.quantity)).toEqual([3, 2, 1])
    })

    it('should sort by amount using quantity multiplied by price', async () => {
      const items = await mountAndSort('Amount', 1)

      expect(items.map((item: { amount: number }) => item.amount)).toEqual([50, 300, 1800])
    })

    it('should sort by instrument name rather than instrument id', async () => {
      const items = await mountAndSort('Instrument', 1)

      expect(items.map((item: { name: string }) => item.name)).toEqual([
        'Ångpanneföreningen AB',
        'Apple Inc',
        'Zalando SE',
      ])
    })
  })
})
