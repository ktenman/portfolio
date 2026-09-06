const BENCHMARK_CHAIN = ['WEBN:GER:EUR', 'VWCE:GER:EUR'] as const

export const resolveBenchmark = (symbols: string[]): string | undefined =>
  BENCHMARK_CHAIN.find(symbol => symbols.includes(symbol))

export const benchmarkLabel = (symbol: string): string => symbol.split(':')[0]
