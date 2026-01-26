export interface AllocationInput {
  instrumentId: number
  value: number
  currentValue?: number
}

export interface CachedState {
  allocations: AllocationInput[]
  inputMode: 'percentage' | 'amount'
  selectedPlatform?: string | null
  optimizeEnabled?: boolean
  totalInvestment?: number
}
