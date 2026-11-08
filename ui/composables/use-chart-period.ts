import { computed, ref, toRaw, watchEffect, type Ref } from 'vue'
import type { PortfolioSummaryDto, TimeRange } from '../models/generated/domain-models'
import type { ChartSummary } from './use-portfolio-chart'
import { sortSummariesByDateAsc } from '../services/summary-aggregator'
import {
  clampPeriod,
  describePeriod,
  periodChange,
  periodLabel,
  type ChartPeriod,
} from '../services/chart-period'

const boundsOf = (summaries: { date: string }[]): ChartPeriod | null => {
  const [first] = summaries
  return first ? { from: first.date, to: summaries[summaries.length - 1].date } : null
}

const within = <T extends { date: string }>(summaries: T[], window: ChartPeriod | null): T[] =>
  window ? summaries.filter(({ date }) => date >= window.from && date <= window.to) : summaries

const covers = (period: ChartPeriod, bounds: ChartPeriod): boolean =>
  period.from <= bounds.from && period.to >= bounds.to

const clampTo = (period: ChartPeriod | null, bounds: ChartPeriod | null): ChartPeriod | null =>
  period && bounds ? clampPeriod(period, bounds) : null

export function useChartPeriod(
  range: Ref<TimeRange>,
  chartSummaries: Ref<ChartSummary[]>,
  performanceSummaries: Ref<PortfolioSummaryDto[]>
) {
  const drawn = ref<ChartPeriod | null>(null)
  const pinned = ref<ChartPeriod | null>(null)

  const ordered = computed(() => sortSummariesByDateAsc(performanceSummaries.value).map(toRaw))
  const loaded = computed(() => boundsOf(ordered.value))
  const today = computed(() => loaded.value?.to ?? '')
  const zoom = computed(() => clampTo(pinned.value, loaded.value))
  const zoomedPerformance = computed(() => within(ordered.value, zoom.value))
  const zoomedChart = computed(() => within(chartSummaries.value, zoom.value))
  const view = computed(() => boundsOf(zoomedPerformance.value))
  const period = computed({
    get: () => clampTo(drawn.value, view.value),
    set: (next: ChartPeriod | null) => {
      drawn.value = next
    },
  })

  watchEffect(() => {
    if (pinned.value && !zoom.value) pinned.value = null
  })

  watchEffect(() => {
    if (drawn.value && !period.value) drawn.value = null
  })

  const change = computed(() => {
    const shown = period.value ?? zoom.value
    if (!shown) return null
    return {
      ...periodChange(ordered.value, shown),
      phrase: describePeriod(shown, today.value),
    }
  })

  const zoomLabel = computed(() => (zoom.value ? periodLabel(zoom.value, today.value) : null))
  const backLabel = computed(() => (zoom.value ? range.value : null))

  const zoomIn = () => {
    const picked = period.value
    const bounds = view.value
    drawn.value = null
    if (picked && bounds && !covers(picked, bounds)) pinned.value = picked
  }

  const back = () => {
    drawn.value = null
    pinned.value = null
  }

  const pick = (next: TimeRange) => {
    back()
    range.value = next
  }

  return {
    period,
    today,
    chartSummaries: zoomedChart,
    performanceSummaries: zoomedPerformance,
    change,
    zoomLabel,
    backLabel,
    zoomIn,
    back,
    pick,
  }
}
