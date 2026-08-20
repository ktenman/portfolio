import type { EtfDetailDto } from '../../models/generated/domain-models'

export interface AllocationInput {
  instrumentId: number
  value: number
  currentValue?: number
}

export type ActionDisplayMode = 'units' | 'amount'

export interface AllocationProps {
  readonly allocations: AllocationInput[]
  readonly availableEtfs: EtfDetailDto[]
  readonly totalInvestment: number
  readonly currentHoldingsTotal: number
  readonly selectedPlatforms: string[]
  readonly optimizeEnabled: boolean
  readonly buyOnlyEnabled: boolean
  readonly actionDisplayMode: ActionDisplayMode
}

export interface CachedState {
  allocations: AllocationInput[]
  inputMode: 'percentage'
  selectedPlatforms?: string[]
  optimizeEnabled?: boolean
  totalInvestment?: number
  actionDisplayMode?: ActionDisplayMode
  buyOnlyEnabled?: boolean
}
