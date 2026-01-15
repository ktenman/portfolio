export interface AllocationInput {
  instrumentId: number
  value: number
}

export interface CachedState {
  allocations: AllocationInput[]
  inputMode: 'percentage' | 'amount'
}
