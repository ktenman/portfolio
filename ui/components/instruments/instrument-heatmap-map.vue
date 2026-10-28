<template>
  <div
    ref="map"
    role="region"
    aria-label="Heatmap of positions"
    class="relative size-full"
    @keydown.escape="emit('select', null)"
  >
    <button
      v-for="box in boxes"
      :key="box.instrument.symbol"
      type="button"
      class="absolute flex flex-col items-center justify-center gap-0.5 overflow-hidden rounded-[4px] p-1 text-center leading-tight"
      :class="box.fill"
      :style="box.style"
      :aria-label="box.label"
      :aria-pressed="box.instrument.id === selectedId"
      @pointerenter="emit('preview', box.instrument.id)"
      @pointerleave="emit('preview', null)"
      @focus="emit('preview', box.instrument.id)"
      @blur="emit('preview', null)"
      @click="emit('select', box.instrument.id === selectedId ? null : box.instrument.id)"
    >
      <template v-if="box.content">
        <instrument-logo
          v-if="box.content.logo"
          class="mb-1"
          :logo-uuid="box.instrument.logoUuid"
          :symbol="box.ticker"
          :size="box.content.logo"
        />
        <span :class="box.content.ticker">{{ box.ticker }}</span>
        <span v-if="box.content.name" class="line-clamp-2 max-w-full text-[13px]">
          {{ box.instrument.name }}
        </span>
        <span class="tabular-nums" :class="box.content.change">{{ box.change }}</span>
      </template>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, useTemplateRef } from 'vue'
import { useElementSize } from '@vueuse/core'
import type { InstrumentDto, TimeRange } from '../../models/generated/domain-models'
import { STEP_CLASSES, stepFor } from '../../services/daily-profit-calendar'
import {
  HEATMAP_STEPS,
  type HeatmapTier,
  boxLabel,
  squarify,
  tickerOf,
  tierFor,
} from '../../services/instrument-heatmap'
import { formatPercentChange } from '../../utils/formatters'
import InstrumentLogo from './instrument-logo.vue'

interface TierContent {
  logo?: number
  ticker: string
  name?: boolean
  change: string
}

const INSET = 1
const MISSING_FILL = 'border border-dashed border-ink-faint bg-surface text-ink'
const SELECTED_RING = 'inset 0 0 0 1px var(--color-surface), inset 0 0 0 3px var(--color-ink)'
const TIER_CONTENT: Record<HeatmapTier, TierContent | null> = {
  xl: {
    logo: 44,
    ticker: 'text-[17px] font-semibold',
    name: true,
    change: 'text-[30px] font-[550]',
  },
  lg: { logo: 32, ticker: 'text-[14px] font-semibold', change: 'text-[20px] font-[550]' },
  md: { logo: 22, ticker: 'text-[12px] font-semibold', change: 'text-[14px] font-[550]' },
  sm: { ticker: 'text-[11px] font-semibold', change: 'text-[11px]' },
  xs: null,
}

const props = defineProps<{
  positions: InstrumentDto[]
  total: number
  period: TimeRange
  selectedId: number | null
  wide: boolean
}>()

const emit = defineEmits<{
  preview: [id: number | null]
  select: [id: number | null]
}>()

const { width, height } = useElementSize(useTemplateRef<HTMLElement>('map'))

const fillOf = (percent: number | null | undefined): string =>
  percent == null ? MISSING_FILL : STEP_CLASSES[stepFor(percent, HEATMAP_STEPS[props.period], 2)]

const boxes = computed(() =>
  squarify(
    props.positions.map(instrument => ({ item: instrument, value: instrument.currentValue ?? 0 })),
    width.value,
    height.value
  ).map(({ item: instrument, x, y, width: boxWidth, height: boxHeight }) => {
    const drawnWidth = Math.max(0, boxWidth - 2 * INSET)
    const drawnHeight = Math.max(0, boxHeight - 2 * INSET)
    const percent = instrument.priceChangePercent
    return {
      instrument,
      ticker: tickerOf(instrument.symbol),
      label: boxLabel(
        instrument,
        ((instrument.currentValue ?? 0) / props.total) * 100,
        props.period
      ),
      fill: fillOf(percent),
      change: percent == null ? '—' : formatPercentChange(percent),
      content: TIER_CONTENT[tierFor(drawnWidth, drawnHeight, props.wide)],
      style: {
        left: `${x + INSET}px`,
        top: `${y + INSET}px`,
        width: `${drawnWidth}px`,
        height: `${drawnHeight}px`,
        boxShadow: instrument.id === props.selectedId ? SELECTED_RING : undefined,
      },
    }
  })
)
</script>
