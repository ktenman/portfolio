export interface CalculationResult {
  xirrs: Transaction[]
  median: number
  average: number
}

export interface Transaction {
  date: string
  amount: number
}
