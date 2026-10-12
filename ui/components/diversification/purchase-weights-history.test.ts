import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import PurchaseWeightsHistory from './purchase-weights-history.vue'
import { transactionsService } from '../../services/api'
import { STORAGE_KEYS } from '../../constants'
import { createTransactionDto } from '../../tests/fixtures'
import {
  TransactionType,
  type EtfDetailDto,
  type TransactionResponseDto,
} from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({ transactionsService: { getAll: vi.fn() } }))

enableAutoUnmount(afterEach)

const { BUY, SELL } = TransactionType

const etf = (instrumentId: number, symbol: string): EtfDetailDto => ({
  instrumentId,
  symbol,
  name: `Fond ${symbol} õ`,
  allocation: 0,
  ter: null,
  annualReturn: null,
  currentPrice: null,
  fundCurrency: null,
  constituentSymbols: [],
})

const ETFS = [etf(1, 'AIFS:GER:EUR'), etf(3, 'LSMC:GER:EUR'), etf(4, 'VVSM:GER:EUR')]

const trade = (
  id: number,
  transactionDate: string,
  instrumentId: number,
  transactionType: TransactionType,
  quantity: number,
  price: number
): TransactionResponseDto =>
  createTransactionDto({ id, transactionDate, instrumentId, transactionType, quantity, price })

const TRADES = [
  trade(1, '2026-06-16', 1, BUY, 10, 100),
  trade(2, '2026-06-16', 3, BUY, 10, 100),
  trade(3, '2026-08-14', 3, SELL, 10, 120),
  trade(4, '2026-08-14', 4, BUY, 12, 100),
]

const respond = (transactions: TransactionResponseDto[]) =>
  vi.mocked(transactionsService.getAll).mockResolvedValue({
    transactions,
    summary: { totalRealizedProfit: 0, totalUnrealizedProfit: 0, totalProfit: 0, netInvested: 0 },
  })

const mountHistory = (platforms: string[] = ['LHV', 'TRADING212']) =>
  mount(PurchaseWeightsHistory, {
    props: { etfs: ETFS, platforms },
    global: {
      plugins: [
        [
          VueQueryPlugin,
          { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) },
        ],
      ],
    },
  })

type History = ReturnType<typeof mountHistory>

const spinner = (wrapper: History) => wrapper.find('.spinner-border').exists()

const loaded = async (wrapper: History) => {
  await vi.waitFor(() => expect(spinner(wrapper)).toBe(false))
  return wrapper
}

const cells = (wrapper: History) =>
  wrapper.findAll('tbody tr').map(row => row.findAll('td').map(cell => cell.text()))

const dayButtons = (wrapper: History) => wrapper.findAll('thead button')

describe('PurchaseWeightsHistory', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    respond(TRADES)
  })

  it('shows each ETF share of the money invested so far when first opened', async () => {
    const wrapper = await loaded(mountHistory())
    expect(cells(wrapper)).toEqual([
      ['50.0', '45.5'],
      ['50.0', ''],
      ['', '54.5'],
    ])
  })

  it('shows each ETF share of the money bought that day after the switch is pressed', async () => {
    const wrapper = await loaded(mountHistory())
    await wrapper.find('button[aria-pressed="false"]').trigger('click')
    expect(cells(wrapper)).toEqual([
      ['50.0', ''],
      ['50.0', 'sold'],
      ['', '100.0'],
    ])
  })

  it('stores the chosen share', async () => {
    const wrapper = await loaded(mountHistory())
    await wrapper.find('button[aria-pressed="false"]').trigger('click')
    expect(localStorage.getItem(STORAGE_KEYS.PURCHASE_WEIGHTS_METRIC)).toBe('bought')
  })

  it('opens on the share chosen last time', async () => {
    localStorage.setItem(STORAGE_KEYS.PURCHASE_WEIGHTS_METRIC, 'bought')
    const wrapper = await loaded(mountHistory())
    expect(wrapper.find('button[aria-pressed="true"]').text()).toBe('Bought that day')
  })

  it('requests the transactions of the selected platforms', async () => {
    await loaded(mountHistory(['LHV', 'TRADING212']))
    expect(transactionsService.getAll).toHaveBeenCalledWith(['LHV', 'TRADING212'])
  })

  it('requests nothing and renders nothing when no platform is selected', async () => {
    const wrapper = await loaded(mountHistory([]))
    expect([
      vi.mocked(transactionsService.getAll).mock.calls.length,
      wrapper.find('section').exists(),
    ]).toEqual([0, false])
  })

  it('renders nothing when the request fails', async () => {
    vi.mocked(transactionsService.getAll).mockRejectedValue(new Error('Ühendus katkes'))
    const wrapper = await loaded(mountHistory())
    expect(wrapper.find('section').exists()).toBe(false)
  })

  it('renders nothing when no transaction maps to an ETF', async () => {
    respond([trade(1, '2026-07-10', 99, BUY, 1, 13.95)])
    const wrapper = await loaded(mountHistory())
    expect(wrapper.find('section').exists()).toBe(false)
  })

  it('shows the heading and a spinner while the transactions load', () => {
    vi.mocked(transactionsService.getAll).mockReturnValue(new Promise(() => {}))
    const wrapper = mountHistory()
    expect([wrapper.find('h3').text(), spinner(wrapper), wrapper.find('table').exists()]).toEqual([
      'Purchase weights',
      true,
      false,
    ])
  })

  it('keeps the previous grid with its day buttons disabled while another selection loads', async () => {
    const wrapper = await loaded(mountHistory(['LHV']))
    vi.mocked(transactionsService.getAll).mockReturnValue(new Promise(() => {}))
    await wrapper.setProps({ platforms: ['TRADING212'] })
    await vi.waitFor(() => expect(transactionsService.getAll).toHaveBeenCalledTimes(2))
    expect(dayButtons(wrapper).map(button => button.attributes('disabled'))).toEqual(['', ''])
  })

  it('titles each day column with its date, kind and money', async () => {
    const wrapper = await loaded(mountHistory())
    expect(dayButtons(wrapper).map(button => button.attributes('title'))).toEqual([
      '16 Jun 2026 · Buy · bought €2,000.00',
      '14 Aug 2026 · Swap or rebalance · bought €1,200.00, sold €1,200.00',
    ])
  })

  it('hands the shares of the clicked day over as targets', async () => {
    const wrapper = await loaded(mountHistory())
    await dayButtons(wrapper)[1].trigger('click')
    expect(wrapper.emitted('apply')).toEqual([
      [
        '14 Aug 2026',
        [
          { instrumentId: 1, value: 45.5 },
          { instrumentId: 4, value: 54.5 },
        ],
      ],
    ])
  })

  it('hands over the shares bought that day once that view is shown', async () => {
    const wrapper = await loaded(mountHistory())
    await wrapper.find('button[aria-pressed="false"]').trigger('click')
    await dayButtons(wrapper)[1].trigger('click')
    expect(wrapper.emitted('apply')).toEqual([['14 Aug 2026', [{ instrumentId: 4, value: 100 }]]])
  })

  it('offers no targets from a day that shows no share', async () => {
    respond([trade(1, '2026-06-16', 1, BUY, 10, 100), trade(2, '2026-07-16', 1, SELL, 5, 100)])
    const wrapper = await loaded(mountHistory())
    await wrapper.find('button[aria-pressed="false"]').trigger('click')
    expect(dayButtons(wrapper).map(button => button.attributes('disabled'))).toEqual([
      undefined,
      '',
    ])
  })

  it('names the kind of each day for screen readers', async () => {
    const wrapper = await loaded(mountHistory())
    expect(wrapper.findAll('thead .sr-only').map(label => label.text())).toEqual([
      'Buy',
      'Swap or rebalance',
    ])
  })

  it('names the three markers in a legend', async () => {
    const wrapper = await loaded(mountHistory())
    expect(wrapper.findAll('.legend li').map(item => item.text())).toEqual([
      'Buy',
      'Swap or rebalance',
      'Cash',
    ])
  })

  it('labels each row with the ticker of its ETF', async () => {
    const wrapper = await loaded(mountHistory())
    expect(wrapper.findAll('tbody th').map(label => label.text())).toEqual(['AIFS', 'LSMC', 'VVSM'])
  })
})
