import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { useDiversificationResult } from './use-diversification-result'
import { diversificationService } from '../services/api'
import type {
  DiversificationCalculatorResponseDto,
  EtfDetailDto,
} from '../models/generated/domain-models'

vi.mock('../services/api', () => ({ diversificationService: { calculate: vi.fn() } }))
vi.mock('@vueuse/core', () => ({ useDebounceFn: vi.fn((fn: () => void) => fn) }))

const response = (sector: string, percentage: number): DiversificationCalculatorResponseDto => ({
  weightedTer: 0,
  weightedAnnualReturn: 0,
  totalUniqueHoldings: 1,
  holdings: [],
  sectors: [{ sector, percentage }],
  industries: [],
  countries: [],
  concentration: { top10Percentage: 0, largestPosition: null },
})

const etf = (instrumentId: number, symbol: string) => ({ instrumentId, symbol }) as EtfDetailDto

const isBenchmarkCall = ([allocations]: [{ percentage: number }[]]) =>
  allocations.length === 1 && allocations[0].percentage === 100

describe('useDiversificationResult', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(diversificationService.calculate).mockResolvedValue(response('Finance', 10))
  })

  it('fetches the benchmark once with a single 100 percent allocation and not again on edits', async () => {
    const allocations = ref([{ instrumentId: 1, value: 60 }])
    const etfs = ref([etf(1, 'TSTA:GER:EUR'), etf(7, 'WEBN:GER:EUR')])
    const { debouncedCalculate } = useDiversificationResult(allocations, etfs)
    await debouncedCalculate()
    allocations.value = [{ instrumentId: 1, value: 70 }]
    await debouncedCalculate()
    const calls = vi.mocked(diversificationService.calculate).mock.calls.filter(isBenchmarkCall)
    expect(calls).toEqual([[[{ instrumentId: 7, percentage: 100 }]]])
  })

  it('exposes no benchmark when the chain resolves nothing', async () => {
    const etfs = ref([etf(1, 'TSTA:GER:EUR')])
    const { debouncedCalculate, benchmarkBreakdowns } = useDiversificationResult(
      ref([{ instrumentId: 1, value: 60 }]),
      etfs
    )
    await debouncedCalculate()
    expect(benchmarkBreakdowns.value).toBeNull()
  })

  it('exposes no benchmark when the allocation is only the benchmark fund', async () => {
    const etfs = ref([etf(7, 'WEBN:GER:EUR')])
    const { debouncedCalculate, benchmarkBreakdowns } = useDiversificationResult(
      ref([{ instrumentId: 7, value: 100 }]),
      etfs
    )
    await debouncedCalculate()
    expect(benchmarkBreakdowns.value).toBeNull()
  })

  it('exposes the benchmark breakdowns once loaded', async () => {
    const etfs = ref([etf(1, 'TSTA:GER:EUR'), etf(7, 'WEBN:GER:EUR')])
    const { debouncedCalculate, benchmarkBreakdowns } = useDiversificationResult(
      ref([{ instrumentId: 1, value: 60 }]),
      etfs
    )
    await debouncedCalculate()
    expect(benchmarkBreakdowns.value?.sectors).toEqual([{ label: 'Finance', value: 10 }])
  })

  it('hides the comparison when the benchmark fund leaves the ETF list', async () => {
    const etfs = ref([etf(1, 'TSTA:GER:EUR'), etf(7, 'WEBN:GER:EUR')])
    const { debouncedCalculate, benchmarkBreakdowns } = useDiversificationResult(
      ref([{ instrumentId: 1, value: 60 }]),
      etfs
    )
    await debouncedCalculate()
    etfs.value = [etf(1, 'TSTA:GER:EUR')]
    expect(benchmarkBreakdowns.value).toBeNull()
  })

  it('leaves the benchmark null after a failed fetch', async () => {
    vi.mocked(diversificationService.calculate).mockImplementation(allocations =>
      isBenchmarkCall([allocations])
        ? Promise.reject(new Error('boom'))
        : Promise.resolve(response('Finance', 10))
    )
    const etfs = ref([etf(1, 'TSTA:GER:EUR'), etf(7, 'WEBN:GER:EUR')])
    const { debouncedCalculate, benchmarkBreakdowns, result } = useDiversificationResult(
      ref([{ instrumentId: 1, value: 60 }]),
      etfs
    )
    await debouncedCalculate()
    await nextTick()
    expect(result.value).not.toBeNull()
    expect(benchmarkBreakdowns.value).toBeNull()
  })

  it('maps industries and country codes into the breakdowns', async () => {
    vi.mocked(diversificationService.calculate).mockResolvedValue({
      ...response('Finance', 10),
      industries: [{ industry: 'Banks', percentage: 8 }],
      countries: [{ countryCode: 'ES', countryName: 'Spain', percentage: 5 }],
    })
    const { debouncedCalculate, breakdowns } = useDiversificationResult(
      ref([{ instrumentId: 1, value: 60 }]),
      ref([etf(1, 'TSTA:GER:EUR')])
    )
    await debouncedCalculate()
    expect(breakdowns.value.industries).toEqual([{ label: 'Banks', value: 8 }])
    expect(breakdowns.value.countries).toEqual([{ label: 'Spain', value: 5, code: 'ES' }])
  })

  it('resolves the benchmark label to the symbol part', () => {
    const { benchmarkLabel } = useDiversificationResult(ref([]), ref([etf(7, 'WEBN:GER:EUR')]))
    expect(benchmarkLabel.value).toBe('WEBN')
  })

  it('falls back to VWCE when WEBN is not held', () => {
    const etfs = ref([etf(1, 'TSTA:GER:EUR'), etf(3, 'VWCE:GER:EUR')])
    const { benchmarkLabel } = useDiversificationResult(ref([]), etfs)
    expect(benchmarkLabel.value).toBe('VWCE')
  })
})
