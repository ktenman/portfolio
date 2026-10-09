<template>
  <section v-if="shown" class="purchase-weights card-shell">
    <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
      <h3 class="m-0 text-base">Purchase weights</h3>
      <SpinnerRing
        v-if="isLoading"
        size="sm"
        class="text-signal-indigo"
        label="Loading purchase weights..."
      />
      <template v-else>
        <div class="ml-auto flex gap-1.5" role="group" aria-label="Share shown">
          <button
            v-for="option in METRICS"
            :key="option.key"
            class="platform-btn"
            :class="{ active: option.key === metric }"
            :aria-pressed="option.key === metric"
            type="button"
            @click="stored = option.key"
          >
            {{ option.label }}
          </button>
        </div>
        <div
          class="flex items-center gap-1 font-mono text-[0.6875rem] text-ink-soft"
          role="img"
          aria-label="Shading from 0% to 40% and above"
        >
          0%
          <span
            v-for="share in SCALE"
            :key="share"
            class="h-2.5 w-4 rounded-[0.125rem]"
            :style="{ background: tint(share) }"
          />
          40%+
        </div>
      </template>
    </div>
    <template v-if="!isLoading">
      <ul
        class="legend m-0 mt-2 flex list-none flex-wrap gap-x-3 gap-y-1 p-0 text-label text-ink-soft"
      >
        <li v-for="kind in KINDS" :key="kind" class="flex items-center gap-1">
          <svg class="size-2.5" viewBox="0 0 12 12" aria-hidden="true">
            <path :d="GLYPHS[kind].path" :fill="GLYPHS[kind].fill" />
          </svg>
          {{ KIND_LABELS[kind] }}
        </li>
      </ul>
      <div
        class="mt-2 flex flex-row-reverse overflow-x-auto"
        role="region"
        aria-label="Purchase weights by trade day"
        tabindex="0"
      >
        <table class="mr-auto flex-none border-separate border-spacing-0.5 text-2xs tabular-nums">
          <thead>
            <tr>
              <th scope="col">Share, %</th>
              <th v-for="day in weights.days" :key="day.date" scope="col" :title="day.title">
                <svg class="mx-auto mb-0.5 block size-2.5" viewBox="0 0 12 12" aria-hidden="true">
                  <path :d="GLYPHS[day.kind].path" :fill="GLYPHS[day.kind].fill" />
                </svg>
                <span class="sr-only">{{ KIND_LABELS[day.kind] }}</span>
                {{ day.day }} {{ day.month }}
                <span v-if="day.year" class="block">{{ day.year }}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.instrumentId">
              <th scope="row">{{ formatTickerSymbol(row.symbol) }}</th>
              <td
                v-for="(cell, index) in row.cells"
                :key="index"
                :style="typeof cell === 'number' ? { background: tint(cell) } : undefined"
              >
                <template v-if="cell === 'sold'">
                  <svg class="inline size-2" viewBox="0 0 12 12" aria-hidden="true">
                    <path :d="GLYPHS.sold.path" :fill="GLYPHS.sold.fill" />
                  </svg>
                  <span class="text-[0.6875rem] text-ink-soft">sold</span>
                </template>
                <template v-else-if="cell !== null">{{ cell.toFixed(1) }}</template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useLocalStorage } from '@vueuse/core'
import SpinnerRing from '../shared/spinner-ring.vue'
import { STORAGE_KEYS } from '../../constants'
import { transactionsService } from '../../services/api'
import {
  buildPurchaseWeights,
  KIND_LABELS,
  shade,
  type DayKind,
  type WeightMetric,
} from '../../services/purchase-weights-history'
import { formatTickerSymbol } from '../../utils/ticker-symbol'
import type { EtfDetailDto } from '../../models/generated/domain-models'

const props = defineProps<{ etfs: EtfDetailDto[]; platforms: string[] }>()

const KINDS: DayKind[] = ['buy', 'swap', 'cash']
const METRICS: { key: WeightMetric; label: string }[] = [
  { key: 'invested', label: 'Invested so far' },
  { key: 'bought', label: 'Bought that day' },
]
const SCALE = [4, 12, 22, 32, 40]
const GLYPHS = {
  buy: { path: 'M6 1.2 11 10.2H1Z', fill: 'var(--color-gain)' },
  swap: { path: 'M6 0.8 11.2 6 6 11.2 0.8 6Z', fill: 'oklch(0.7 0.12 74)' },
  cash: { path: 'M6 1.5a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9Z', fill: 'var(--color-notice)' },
  sold: { path: 'M6 10.8 11 1.8H1Z', fill: 'var(--color-loss)' },
}

const stored = useLocalStorage<WeightMetric>(STORAGE_KEYS.PURCHASE_WEIGHTS_METRIC, 'invested')
const metric = computed<WeightMetric>(() => (stored.value === 'bought' ? 'bought' : 'invested'))

const enabled = computed(() => props.platforms.length > 0)
const platformsKey = computed(() => [...props.platforms].sort().join(','))

const { data, isLoading } = useQuery({
  queryKey: ['transactions', 'purchase-weights-history', platformsKey],
  queryFn: () => transactionsService.getAll(props.platforms),
  enabled,
})

const weights = computed(() => buildPurchaseWeights(props.etfs, data.value?.transactions ?? []))
const shown = computed(() => enabled.value && (isLoading.value || weights.value.days.length > 0))
const rows = computed(() =>
  weights.value.rows.map(row => ({
    ...row,
    cells: metric.value === 'bought' ? row.bought : row.invested,
  }))
)

const tint = (share: number): string =>
  `color-mix(in oklch, var(--color-brass) ${shade(share)}%, var(--color-surface))`
</script>

<style scoped>
thead th {
  min-width: 3.125rem;
  padding: 0 0.25rem 0.25rem;
  font: 400 0.6875rem/1.2 var(--font-mono);
  color: var(--color-ink-soft);
  text-align: center;
  vertical-align: top;
}

tbody th {
  font: 500 var(--text-2xs) var(--font-mono);
}

th:first-child {
  position: sticky;
  left: 0;
  z-index: 1;
  min-width: 0;
  padding: 0 0.75rem 0 0;
  background: var(--color-surface);
  text-align: left;
  vertical-align: bottom;
  white-space: nowrap;
}

td {
  min-width: 3.125rem;
  height: 1.625rem;
  padding: 0 0.5rem;
  border-radius: 0.1875rem;
  background: color-mix(in oklch, var(--color-ink) 3%, transparent);
  color: var(--color-ink);
  text-align: right;
  white-space: nowrap;
}
</style>
