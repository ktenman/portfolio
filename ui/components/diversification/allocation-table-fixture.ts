import { mount } from '@vue/test-utils'
import AllocationTable from './allocation-table.vue'
import type { EtfDetailDto } from '../../models/generated/domain-models'
import { mockPlatforms } from '../../tests/fixtures'
import { setPlatformDisplayNames } from '../../utils/platform-utils'

setPlatformDisplayNames(mockPlatforms)

export const createEtf = (overrides: Partial<EtfDetailDto> = {}): EtfDetailDto => ({
  instrumentId: 1,
  symbol: 'VWCE',
  name: 'Vanguard FTSE All-World',
  allocation: 0,
  ter: 0.22,
  annualReturn: 0.12,
  currentPrice: 120.5,
  fundCurrency: null,
  ...overrides,
})

const defaultEtfs = [
  createEtf(),
  createEtf({
    instrumentId: 2,
    symbol: 'VUAA',
    name: 'Vanguard S&P 500',
    ter: 0.07,
    annualReturn: 0.15,
    currentPrice: 95.3,
  }),
]

const defaultProps = {
  allocations: [{ instrumentId: 0, value: 0 }],
  availableEtfs: defaultEtfs,
  isLoadingPortfolio: false,
  totalInvestment: 0,
  selectedPlatforms: [] as string[],
  availablePlatforms: [] as string[],
  currentHoldingsTotal: 0,
  optimizeEnabled: false,
  buyOnlyEnabled: false,
  actionDisplayMode: 'units' as const,
}

export const fullyInvested = {
  totalInvestment: 10000,
  allocations: [{ instrumentId: 1, value: 100 }],
}

export const allocationPair = [
  { instrumentId: 1, value: 60 },
  { instrumentId: 2, value: 40 },
]

export const createWrapper = (
  props: Record<string, unknown> = {},
  options: { attachTo?: HTMLElement } = {}
) => mount(AllocationTable, { props: { ...defaultProps, ...props }, ...options })
