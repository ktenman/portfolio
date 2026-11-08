<template>
  <div class="period-layer" :class="{ compact }">
    <span
      v-for="chip in chips"
      :key="chip.part"
      class="period-chip"
      data-testid="period-chip"
      :data-side="chip.side"
      :style="{ left: px(chip.x), top: px(frame.axisY) }"
    >
      {{ chip.text }}
    </span>
    <template v-if="settled">
      <span
        v-for="edge in edges"
        :key="`hit-${edge.part}`"
        class="edge-hit"
        :data-part="edge.part"
        :style="{ left: px(edge.x), top: px(frame.top), height: px(hitHeight) }"
      />
      <span
        v-for="edge in edges"
        :key="edge.part"
        class="grip"
        role="slider"
        tabindex="0"
        :data-part="edge.part"
        :data-active="drag === edge.part || undefined"
        :aria-label="edge.label"
        :aria-valuemin="edge.min"
        :aria-valuemax="edge.max"
        :aria-valuenow="edge.now"
        :aria-valuetext="edge.text"
        :style="{ left: px(edge.x), top: px(gripY) }"
        @keydown="onKey(edge.part, $event)"
      />
    </template>
    <span
      v-if="ring"
      class="ring"
      data-testid="period-ring"
      :style="{ left: px(ring.x), top: px(ring.y) }"
    />
    <div
      ref="aside"
      class="aside"
      :class="{ unfit: hintHidden, spread }"
      :style="{
        left: spread ? px(frame.legendLeft) : undefined,
        right: px(frame.width - asideRight),
        top: px(frame.legendY),
      }"
    >
      <template v-if="aside === 'acts'">
        <span class="days" data-testid="period-days">
          {{ days }} days
          <button
            type="button"
            class="clear"
            data-testid="period-clear"
            aria-label="Clear period"
            @click="emit('update:period', null)"
          >
            <svg v-bind="ICON" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12" /></svg>
          </button>
        </span>
        <button type="button" class="zoom" data-testid="period-zoom" @click="zoomFromButton">
          <svg v-bind="ICON" aria-hidden="true">
            <circle cx="11" cy="11" r="7" />
            <path d="m21 21-5-5M11 8v6M8 11h6" />
          </svg>
          Zoom in
        </button>
      </template>
      <button
        v-else-if="aside === 'back'"
        ref="back"
        type="button"
        class="back"
        data-testid="period-back"
        @click="emit('back')"
      >
        <svg v-bind="ICON" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>
        Back to {{ backTo }}
      </button>
      <span v-else class="hint" :class="{ drawing: aside === 'drawing' }">
        <svg v-if="!compact" v-bind="ICON" stroke-width="2" aria-hidden="true">
          <path d="M3 12h18M7 8l-4 4 4 4M17 8l4 4-4 4" />
        </svg>
        {{ hint }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, useTemplateRef, watch } from 'vue'
import { useElementSize } from '@vueuse/core'
import { frameBounds, xOfDate, type ChartFrame } from '../../services/chart-frame'
import {
  MIN_PERIOD_DAYS,
  chipDate,
  longDate,
  periodDays,
  stepEdge,
  type ChartPeriod,
  type PeriodEdge,
} from '../../services/chart-period'
import type { PeriodDrag } from '../../composables/use-period-gesture'
import { daysBetween } from '../../utils/iso-dates'

type ChipSide = 'left' | 'right' | 'centre'
type Aside = 'acts' | 'back' | 'idle' | 'drawing'

const props = defineProps<{
  frame: ChartFrame
  period: ChartPeriod | null
  drag: PeriodDrag | null
  ring: { x: number; y: number } | null
  today: string
  backTo: string | null
  compact: boolean
}>()

const emit = defineEmits<{
  'update:period': [period: ChartPeriod | null]
  zoom: []
  back: []
  legend: [hidden: boolean]
}>()

const ICON = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  'stroke-width': '2.5',
  'stroke-linecap': 'round',
  'stroke-linejoin': 'round',
} as const

const SPLIT_PX = 96
const CHIP_REACH = 10
const LEGEND_GAP = 16
const COMPACT_INSET = 8

const px = (value: number) => `${value}px`

const settled = computed(() => props.period !== null && props.drag !== 'new')
const days = computed(() => (props.period ? periodDays(props.period) : 0))
const hitHeight = computed(() => props.frame.axisY + CHIP_REACH - props.frame.top)
const gripY = computed(() => (props.frame.top + props.frame.bottom) / 2)

const bounds = computed(() => frameBounds(props.frame))

const offset = (date: string) => daysBetween(bounds.value.from, date)

const edges = computed(() => {
  const period = props.period
  if (!period) return []
  const edge = (part: PeriodEdge, text: string, min: number, max: number) => ({
    part,
    label: text,
    min,
    max,
    x: xOfDate(props.frame, period[part]),
    now: offset(period[part]),
    text: longDate(period[part]),
  })
  return [
    edge('from', 'Start of period', 0, offset(period.to) - MIN_PERIOD_DAYS),
    edge('to', 'End of period', offset(period.from) + MIN_PERIOD_DAYS, offset(bounds.value.to)),
  ]
})

const chips = computed(() => {
  const period = props.period
  if (!period) return []
  const chip = (part: PeriodEdge, side: ChipSide) => ({
    part,
    side,
    x: xOfDate(props.frame, period[part]),
    text: chipDate(period[part], props.today),
  })
  if (period.from === period.to) return [chip('from', 'centre')]
  const split = xOfDate(props.frame, period.to) - xOfDate(props.frame, period.from) < SPLIT_PX
  return [chip('from', split ? 'left' : 'centre'), chip('to', split ? 'right' : 'centre')]
})

const aside = computed<Aside>(() => {
  if (props.period) return settled.value ? 'acts' : 'drawing'
  return props.backTo ? 'back' : 'idle'
})

const hint = computed(() => {
  if (aside.value === 'idle') {
    return props.compact ? 'Hold, then slide' : 'Drag across the chart to pick a period'
  }
  if (!props.compact) return 'Release, then drag the edges to fine-tune'
  return days.value >= MIN_PERIOD_DAYS
    ? 'Lift, then drag the edges'
    : 'Slide to the other end of the period'
})

const spread = computed(() => props.compact && props.period !== null)

const asideBox = useTemplateRef<HTMLElement>('aside')
const backButton = useTemplateRef<HTMLButtonElement>('back')
const { width: asideWidth } = useElementSize(asideBox)

const asideRight = computed(() =>
  props.compact ? props.frame.width - COMPACT_INSET : props.frame.right
)
const fits = computed(
  () => asideRight.value - asideWidth.value - LEGEND_GAP >= props.frame.legendRight
)
const hintHidden = computed(() => aside.value === 'idle' && !fits.value)
const legendHidden = computed(() => spread.value || (aside.value !== 'idle' && !fits.value))

watch(legendHidden, hidden => emit('legend', hidden), { immediate: true })

const zoomFromKeys = async () => {
  emit('zoom')
  await nextTick()
  backButton.value?.focus()
}

const zoomFromButton = (event: MouseEvent) => (event.detail === 0 ? zoomFromKeys() : emit('zoom'))

const onKey = (part: PeriodEdge, event: KeyboardEvent) => {
  if (!props.period) return
  if (event.key === 'Enter') return zoomFromKeys()
  const next = stepEdge(props.period, part, event.key, event.shiftKey, bounds.value)
  if (!next) return
  event.preventDefault()
  emit('update:period', next)
}
</script>

<style scoped src="./chart-period-layer.css"></style>
