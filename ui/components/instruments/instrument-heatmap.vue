<template>
  <div class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_440px]">
    <div class="min-w-0">
      <div class="overflow-hidden rounded-container border border-hairline bg-surface shadow-card">
        <div class="h-16.5 border-b border-hairline max-md:h-26">
          <instrument-heatmap-readout
            v-if="showsFigures"
            :instrument="shown"
            :total="totalValue"
            :totals="totals"
            @replace-logo="logoTarget = $event"
          />
        </div>
        <div data-map class="h-[340px] p-1 md:h-[520px] lg:h-[630px]">
          <skeleton-loader v-if="isLoading" type="table" :rows="5" :columns="4" />
          <alert-message v-else-if="isError" variant="danger">
            {{ errorMessage || 'Failed to load data. Please try again.' }}
          </alert-message>
          <p
            v-else-if="instruments.length === 0"
            class="m-0 grid size-full place-content-center text-ink-soft"
          >
            No open positions for the selected platforms.
          </p>
          <instrument-heatmap-map
            v-else
            :positions="split.drawn"
            :total="totalValue"
            :period="period"
            :selected-id="selectedId"
            :wide="wide"
            @preview="previewId = $event"
            @select="selectedId = $event"
          />
        </div>
      </div>
      <template v-if="showsFigures">
        <div data-total class="mt-3 hidden items-baseline gap-x-6 text-label md:flex">
          <span class="font-semibold text-ink">Total</span>
          <dl class="m-0 flex flex-wrap gap-x-6">
            <div v-for="figure in totals" :key="figure.label" class="flex gap-1.5">
              <dt class="text-ink-soft">{{ figure.label }}</dt>
              <dd class="m-0 font-semibold text-ink tabular-nums">{{ figure.value }}</dd>
            </div>
          </dl>
        </div>
        <p class="mt-3 mb-0 text-label text-ink-soft">
          Box size is the position's value. Colour is its {{ period }} change in percent.
          <template v-if="split.hidden.length > 0">{{ hiddenText }}</template>
        </p>
        <daily-profit-legend
          :missing="hasMissingChange"
          :steps="HEATMAP_STEPS[period]"
          :missing-label="`No ${period} change`"
        />
      </template>
    </div>
    <instrument-change-list
      v-if="showsFigures"
      class="self-start rounded-container border border-hairline bg-surface p-4 shadow-card"
      :positions="instruments"
      :period="period"
      :interactive="interactive"
      @preview="previewId = $event"
      @select="selectedId = $event"
    />
    <logo-replacement-modal
      :model-value="logoTarget !== null"
      :holding-uuid="logoTarget?.uuid ?? null"
      :holding-name="logoTarget?.name ?? ''"
      @update:model-value="logoTarget = null"
      @replaced="queryClient.invalidateQueries({ queryKey: ['instruments'] })"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, toRef, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useQueryClient } from '@tanstack/vue-query'
import type { InstrumentDto, TimeRange } from '../../models/generated/domain-models'
import { useInstrumentTotals } from '../../composables/use-instrument-totals'
import { HEATMAP_STEPS, splitDrawable } from '../../services/instrument-heatmap'
import {
  formatCurrencyChange,
  formatCurrencyWithSymbol,
  formatPercentChange,
  formatPercentageFromDecimal,
} from '../../utils/formatters'
import AlertMessage from '../shared/alert-message.vue'
import SkeletonLoader from '../shared/skeleton-loader.vue'
import DailyProfitLegend from '../portfolio/daily-profit-legend.vue'
import LogoReplacementModal from '../etf/logo-replacement-modal.vue'
import InstrumentChangeList from './instrument-change-list.vue'
import InstrumentHeatmapMap from './instrument-heatmap-map.vue'
import InstrumentHeatmapReadout from './instrument-heatmap-readout.vue'

const props = defineProps<{
  instruments: InstrumentDto[]
  portfolioXirr: number | null
  rangeChange: { changeAmount: number; changePercent: number } | undefined
  period: TimeRange
  isLoading: boolean
  isError: boolean
  errorMessage?: string
}>()

const selectedId = ref<number | null>(null)
const previewId = ref<number | null>(null)
const logoTarget = ref<InstrumentDto | null>(null)
const queryClient = useQueryClient()
const wide = useMediaQuery('(min-width: 768px)')
const interactive = useMediaQuery('(min-width: 1024px)')
const { totalValue } = useInstrumentTotals(toRef(props, 'instruments'))

const split = computed(() => splitDrawable(props.instruments))
const showsFigures = computed(
  () => !props.isLoading && !props.isError && props.instruments.length > 0
)
const hasMissingChange = computed(() =>
  props.instruments.some(({ priceChangePercent }) => priceChangePercent == null)
)

const shown = computed(() => {
  const id = previewId.value ?? selectedId.value
  if (id === null) return null
  return props.instruments.find(instrument => instrument.id === id) ?? null
})

const totals = computed(() => {
  const change = props.rangeChange
  return [
    { label: 'Value', value: formatCurrencyWithSymbol(totalValue.value) },
    {
      label: `${props.period} change`,
      value: change
        ? `${formatCurrencyChange(change.changeAmount)} / ${formatPercentChange(change.changePercent)}`
        : '—',
    },
    {
      label: 'XIRR',
      value: props.portfolioXirr == null ? '—' : formatPercentageFromDecimal(props.portfolioXirr),
    },
  ]
})

const hiddenText = computed(() => {
  const count = split.value.hidden.length
  return count === 1
    ? '1 position under 0.1% is too small to draw and appears only in the list.'
    : `${count} positions under 0.1% are too small to draw and appear only in the list.`
})

watch(
  () => split.value.drawn.map(({ id }) => id),
  drawn => {
    if (props.isLoading) {
      previewId.value = null
      return
    }
    if (selectedId.value !== null && !drawn.includes(selectedId.value)) selectedId.value = null
    if (previewId.value !== null && !drawn.includes(previewId.value)) previewId.value = null
  }
)
</script>
