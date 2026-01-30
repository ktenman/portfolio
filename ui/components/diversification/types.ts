export interface AllocationInput {
  instrumentId: number
  value: number
  currentValue?: number
}

export type ActionDisplayMode = 'units' | 'amount'

export interface CachedState {
  allocations: AllocationInput[]
  inputMode: 'percentage' | 'amount'
  selectedPlatform?: string | null
  optimizeEnabled?: boolean
  totalInvestment?: number
  actionDisplayMode?: ActionDisplayMode
}
