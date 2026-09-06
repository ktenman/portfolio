import { ref, computed } from 'vue'
import type { Ref } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { diversificationService } from '../services/api'
import { resolveBenchmark, benchmarkLabel as symbolPart } from '../constants/benchmarks'
import type { BreakdownItem } from '../services/diversification-chart-service'
import type {
  DiversificationCalculatorResponseDto,
  EtfDetailDto,
} from '../models/generated/domain-models'
import type { AllocationInput } from '../components/diversification/types'

export const getErrorMessage = (e: unknown): string => {
  if (e instanceof Error) {
    if (e.message.includes('Network Error') || e.message.includes('fetch')) {
      return 'Unable to connect to the server. Please check your internet connection and try again.'
    }
    if (e.message.includes('timeout')) {
      return 'The request timed out. Please try again.'
    }
    if (e.message.includes('500') || e.message.includes('Internal Server Error')) {
      return 'A server error occurred. Please try again later.'
    }
    return e.message
  }
  return 'An unexpected error occurred. Please try again.'
}

export interface Breakdowns {
  sectors: BreakdownItem[]
  industries: BreakdownItem[]
  holdings: BreakdownItem[]
  countries: BreakdownItem[]
}

const toBreakdowns = (dto: DiversificationCalculatorResponseDto | null): Breakdowns => ({
  sectors: dto?.sectors.map(s => ({ label: s.sector, value: s.percentage })) ?? [],
  industries: dto?.industries.map(i => ({ label: i.industry, value: i.percentage })) ?? [],
  holdings: dto?.holdings.map(h => ({ label: h.name, value: h.percentage })) ?? [],
  countries:
    dto?.countries.map(c => ({
      label: c.countryName,
      value: c.percentage,
      code: c.countryCode ?? undefined,
    })) ?? [],
})

export function useDiversificationResult(
  allocations: Ref<AllocationInput[]>,
  availableEtfs: Ref<EtfDetailDto[]>
) {
  const result = ref<DiversificationCalculatorResponseDto | null>(null)
  const benchmark = ref<DiversificationCalculatorResponseDto | null>(null)
  const error = ref('')
  const isCalculating = ref(false)
  let benchmarkRequest: Promise<void> | null = null

  const benchmarkEtf = computed(() => {
    const symbol = resolveBenchmark(availableEtfs.value.map(e => e.symbol))
    return availableEtfs.value.find(e => e.symbol === symbol)
  })

  const benchmarkLabel = computed(() => benchmarkEtf.value && symbolPart(benchmarkEtf.value.symbol))

  const loadBenchmark = () => {
    const etf = benchmarkEtf.value
    if (!etf || benchmarkRequest) return
    benchmarkRequest = diversificationService
      .calculate([{ instrumentId: etf.instrumentId, percentage: 100 }])
      .then(dto => {
        benchmark.value = dto
      })
      .catch(() => {
        benchmark.value = null
      })
  }

  const validAllocations = () => allocations.value.filter(a => a.instrumentId > 0 && a.value > 0)

  const calculateDiversification = async () => {
    const valid = validAllocations()
    if (valid.length < 1) {
      result.value = null
      return
    }
    loadBenchmark()
    isCalculating.value = true
    error.value = ''
    try {
      result.value = await diversificationService.calculate(
        valid.map(a => ({ instrumentId: a.instrumentId, percentage: a.value }))
      )
    } catch (e) {
      error.value = getErrorMessage(e)
      result.value = null
    } finally {
      isCalculating.value = false
    }
  }

  const debouncedCalculate = useDebounceFn(calculateDiversification, 500)

  const breakdowns = computed(() => toBreakdowns(result.value))

  const onlyBenchmarkAllocated = computed(() => {
    const ids = validAllocations().map(a => a.instrumentId)
    return ids.length === 1 && ids[0] === benchmarkEtf.value?.instrumentId
  })

  const benchmarkBreakdowns = computed(() =>
    benchmark.value === null || onlyBenchmarkAllocated.value ? null : toBreakdowns(benchmark.value)
  )

  return {
    result,
    error,
    isCalculating,
    debouncedCalculate,
    breakdowns,
    benchmarkBreakdowns,
    benchmarkLabel,
  }
}
