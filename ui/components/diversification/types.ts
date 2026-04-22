export interface AllocationInput {
  instrumentId: number
  value: number
  currentValue?: number
}

export type ActionDisplayMode = 'units' | 'amount'

export interface CachedState {
  allocations: AllocationInput[]
  inputMode: 'percentage'
  selectedPlatforms?: string[]
  optimizeEnabled?: boolean
  totalInvestment?: number
  actionDisplayMode?: ActionDisplayMode
  buyOnlyEnabled?: boolean
}
