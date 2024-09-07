export interface CalculationResult {
  xirrs: Transaction[]
  median: number
  average: number
  total: number
}

export interface Transaction {
  date: string
  amount: number
}
