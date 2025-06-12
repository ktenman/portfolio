interface Transaction {
  date: string
  amount: number
}

export interface CalculationResult {
  xirrs: Transaction[]
  median: number
  average: number
  total: number
}
