<template>
  <div>
    <div
      ref="grid"
      class="month-grid"
      role="application"
      aria-label="Monthly profit calendar"
      tabindex="0"
      :aria-describedby="selectedId"
      @keydown="onKeydown"
      @pointerleave="emit('preview', null)"
    >
      <span
        v-for="(label, index) in COLUMN_LABELS"
        :key="label"
        class="month-label"
        :style="{ gridColumn: index + FIRST_MONTH_COLUMN }"
        aria-hidden="true"
      >
        {{ label }}
      </span>
      <template v-for="year in monthly.years" :key="year.year">
        <span class="year-label" aria-hidden="true">{{ year.year }}</span>
        <span
          v-for="(slot, index) in year.slots"
          :key="index"
          class="month-slot"
          :class="classesOf(slot)"
          v-bind="attributesOf(slot)"
          @click="onClick(slot)"
          @pointerenter="onEnter(slot, $event)"
        >
          <span v-if="slot?.kind === 'observed'" class="month-text">{{ slot.cell }}</span>
        </span>
        <span
          class="year-total"
          :class="totalClass(year.percent)"
          role="img"
          :aria-label="year.name"
          :title="year.name"
        >
          {{ year.percent === null ? '—' : formatPercentChange(year.percent, 1) }}
        </span>
      </template>
    </div>
    <p v-for="year in notedYears" :key="year.year" class="year-note">{{ year.note }}</p>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, useId } from 'vue'
import type { ChangeStep } from '../../services/daily-profit-calendar'
import { moveMonth, type MonthlyProfit, type MonthSlot } from '../../services/monthly-profit'
import { formatPercentChange } from '../../utils/formatters'
import { MONTHS } from '../../utils/iso-dates'

const props = defineProps<{
  monthly: MonthlyProfit
  selected?: string
  previewed?: string | null
}>()

const emit = defineEmits<{ select: [key: string]; preview: [key: string | null] }>()

const COLUMN_LABELS = [...MONTHS, 'Year']
const FIRST_MONTH_COLUMN = 2
const KEY_STEPS = new Map([
  ['ArrowLeft', -1],
  ['ArrowRight', 1],
  ['ArrowUp', 12],
  ['ArrowDown', -12],
])
const STEP_CLASSES: Record<ChangeStep, string> = {
  'loss-4': 'bg-loss text-surface',
  'loss-3': 'bg-loss-step-3 text-ink',
  'loss-2': 'bg-loss-step-2 text-ink',
  'loss-1': 'bg-loss-step-1 text-ink',
  flat: 'bg-change-flat text-ink',
  'gain-1': 'bg-gain-step-1 text-ink',
  'gain-2': 'bg-gain-step-2 text-ink',
  'gain-3': 'bg-gain-step-3 text-ink',
  'gain-4': 'bg-gain text-surface',
}

const grid = ref<HTMLElement | null>(null)
const selectedId = useId()

const notedYears = computed(() => props.monthly.years.filter(year => year.note))

const classesOf = (slot: MonthSlot) => {
  if (!slot) return ''
  if (slot.kind === 'missing') return 'border border-dashed border-ink-faint'
  return [
    'month-cell selectable',
    STEP_CLASSES[slot.step],
    {
      selected: slot.key === props.selected,
      previewed: slot.key === props.previewed && slot.key !== props.selected,
    },
  ]
}

const attributesOf = (slot: MonthSlot) => {
  if (!slot) return { 'aria-hidden': 'true' as const }
  const current =
    slot.key === props.selected ? { id: selectedId, 'aria-current': 'true' as const } : {}
  return { role: 'img', 'aria-label': slot.name, title: slot.name, ...current }
}

const totalClass = (percent: number | null) => {
  if (!percent) return 'text-ink-soft'
  return percent > 0 ? 'text-gain' : 'text-loss'
}

const onClick = (slot: MonthSlot) => {
  if (slot?.kind !== 'observed') return
  grid.value?.focus()
  emit('select', slot.key)
}

const onEnter = (slot: MonthSlot, event: PointerEvent) => {
  if (slot?.kind === 'observed' && event.pointerType !== 'touch') emit('preview', slot.key)
}

const onKeydown = (event: KeyboardEvent) => {
  const step = KEY_STEPS.get(event.key)
  if (!step || !props.selected || event.altKey || event.ctrlKey || event.metaKey) return
  event.preventDefault()
  const key = moveMonth(props.monthly, props.selected, step)
  if (key) emit('select', key)
}
</script>

<style scoped>
.month-grid {
  display: grid;
  grid-template-columns: auto repeat(12, minmax(0, 1fr)) auto;
  grid-auto-rows: 40px;
  grid-template-rows: auto;
  gap: 3px;
  align-items: stretch;
  margin-inline: 2px;
  border-radius: var(--radius-control);
  outline: none;
}

.month-grid:focus-visible {
  outline: 2px solid var(--color-brass);
  outline-offset: 4px;
}

.month-label,
.year-label,
.year-note {
  font-size: var(--text-label);
  line-height: 1;
  color: var(--color-ink-soft);
}

.month-label {
  grid-row: 1;
  padding-bottom: 3px;
  text-align: center;
}

.year-label {
  grid-column: 1;
  align-self: center;
  padding-right: 6px;
  font-variant-numeric: tabular-nums;
}

.month-slot {
  display: grid;
  place-items: center;
  min-width: 0;
  container-type: inline-size;
  border-radius: var(--radius-control);
}

.month-text {
  font-size: clamp(9px, 24cqi, 13px);
  font-variant-numeric: tabular-nums;
  line-height: 1;
  white-space: nowrap;
}

.year-total {
  align-self: center;
  padding-left: 8px;
  font-size: var(--text-2xs);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  text-align: right;
  white-space: nowrap;
}

.selectable {
  cursor: pointer;
}

.selected,
.previewed {
  padding: 2px;
  background-clip: content-box;
  outline: 2px solid var(--color-brass-deep);
}

.previewed {
  outline: 1.5px solid var(--color-ink-faint);
}

.year-note {
  margin: 8px 2px 0;
  line-height: 1.3;
}
</style>
