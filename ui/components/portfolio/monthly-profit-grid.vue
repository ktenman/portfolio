<template>
  <div>
    <div
      ref="grid"
      class="calendar-grid"
      role="application"
      aria-label="Monthly profit calendar"
      tabindex="0"
      :aria-describedby="selectedId"
      @keydown="onKeydown"
      @pointerleave="emit('preview', null)"
    >
      <span
        v-for="(month, index) in MONTHS"
        :key="month"
        class="axis-label column-label"
        :style="{ gridColumn: index + FIRST_MONTH_COLUMN }"
        aria-hidden="true"
      >
        {{ month }}
      </span>
      <span
        class="axis-label column-label total-label"
        :style="{ gridColumn: TOTAL_COLUMN }"
        aria-hidden="true"
      >
        Year
      </span>
      <template v-for="(year, row) in years" :key="year.year">
        <span
          class="axis-label year-label"
          :style="{ gridRow: row + FIRST_YEAR_ROW }"
          aria-hidden="true"
        >
          {{ year.year }}
        </span>
        <template v-for="(slot, index) in year.slots" :key="index">
          <span
            v-if="slot?.kind === 'observed'"
            class="month-cell selectable rounded-control"
            :class="[
              STEP_CLASSES[slot.step],
              {
                selected: slot.key === selected,
                previewed: slot.key === previewed && slot.key !== selected,
              },
            ]"
            :style="placeOf(row, index)"
            role="img"
            :aria-label="slot.name"
            :title="slot.name"
            v-bind="currentOf(slot.key)"
            @click="onClick(slot.key)"
            @pointerenter="onEnter(slot.key, $event)"
          >
            {{ formatMonthCell(slot.percent) }}
          </span>
          <span
            v-else-if="slot"
            class="rounded-control border border-dashed border-ink-faint"
            :style="placeOf(row, index)"
            role="img"
            :aria-label="slot.name"
            :title="slot.name"
          />
        </template>
        <span
          class="year-total"
          :class="percentClass(year.percent)"
          :style="{ gridRow: row + FIRST_YEAR_ROW, gridColumn: TOTAL_COLUMN }"
          role="img"
          :aria-label="year.name"
        >
          {{ year.text }}
        </span>
      </template>
    </div>
    <div class="year-notes">
      <p v-for="note in notes" :key="note">{{ note }}</p>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, useId } from 'vue'
import type { ChangeStep } from '../../services/daily-profit-calendar'
import {
  formatMonthCell,
  moveMonth,
  percentClass,
  type MonthlyProfit,
} from '../../services/monthly-profit'
import { yearNotes, type MonthlyYear } from '../../services/monthly-profit-years'
import { MONTHS } from '../../utils/iso-dates'

const props = defineProps<{
  model: MonthlyProfit
  years: MonthlyYear[]
  selected: string
  previewed: string | null
}>()

const emit = defineEmits<{ select: [key: string]; preview: [key: string | null] }>()

const FIRST_MONTH_COLUMN = 2
const TOTAL_COLUMN = 14
const FIRST_YEAR_ROW = 2
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

const notes = computed(() => yearNotes(props.years))

const placeOf = (row: number, index: number) => ({
  gridRow: row + FIRST_YEAR_ROW,
  gridColumn: index + FIRST_MONTH_COLUMN,
})

const currentOf = (key: string) =>
  key === props.selected ? { id: selectedId, 'aria-current': 'true' as const } : {}

const onClick = (key: string) => {
  grid.value?.focus()
  emit('select', key)
}

const onEnter = (key: string, event: PointerEvent) => {
  if (event.pointerType !== 'touch') emit('preview', key)
}

const onKeydown = (event: KeyboardEvent) => {
  const step = KEY_STEPS.get(event.key)
  if (!step || event.altKey || event.ctrlKey || event.metaKey) return
  event.preventDefault()
  const key = moveMonth(props.model, props.selected, step)
  if (key) emit('select', key)
}
</script>

<style scoped>
.calendar-grid {
  display: grid;
  grid-template-columns: auto repeat(12, minmax(0, 1fr)) auto;
  grid-template-rows: auto;
  grid-auto-rows: 40px;
  gap: 3px;
  margin-inline: 2px;
  border-radius: var(--radius-control);
  outline: none;
  font-size: var(--text-label);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.calendar-grid:focus-visible {
  outline: 2px solid var(--color-brass);
  outline-offset: 4px;
}

.axis-label {
  color: var(--color-ink-soft);
  white-space: nowrap;
}

.column-label {
  grid-row: 1;
  justify-self: center;
  padding-bottom: 5px;
}

.total-label {
  justify-self: end;
}

.year-label {
  grid-column: 1;
  align-self: center;
  padding-right: 6px;
}

.month-cell {
  display: grid;
  place-content: center;
  white-space: nowrap;
}

.year-total {
  align-self: center;
  justify-self: end;
  padding-left: 12px;
  white-space: nowrap;
}

.year-notes {
  margin-top: 10px;
  font-size: var(--text-label);
  line-height: 1.4;
  color: var(--color-ink-soft);
}

.year-notes p {
  margin: 0;
}

.selectable {
  cursor: pointer;
}

.selected,
.previewed {
  padding: 2px;
  background-clip: content-box;
  border-color: transparent;
  outline: 2px solid var(--color-brass-deep);
}

.previewed {
  outline: 1.5px solid var(--color-ink-faint);
}
</style>
