<template>
  <div
    ref="grid"
    class="calendar-grid"
    :class="{ compact }"
    :style="{ '--weeks': calendar.weeks }"
    v-bind="gridAttributes"
    @keydown="onKeydown"
  >
    <span
      v-for="month in calendar.months"
      :key="month.column"
      class="month-label"
      :class="{ 'last-week': calendar.weeks > 1 && month.column === calendar.weeks - 1 }"
      :style="{ gridColumn: month.column + firstWeekColumn }"
      aria-hidden="true"
    >
      {{ month.label }}
    </span>
    <template v-if="!compact">
      <span
        v-for="(label, index) in WEEKDAY_LABELS"
        :key="label"
        class="weekday-label"
        :style="{ gridRow: index * 2 + FIRST_DAY_ROW }"
        aria-hidden="true"
      >
        {{ label }}
      </span>
    </template>
    <daily-profit-cell
      v-for="(day, index) in calendar.days"
      :key="day.date"
      :variant="day.kind === 'counted' ? day.step : day.kind"
      :class="{ selected: day.date === selected, selectable: isSelectable(day) }"
      :style="placeOf(index)"
      v-bind="cellAttributes(day)"
      @click="onClick(day)"
    />
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, useId } from 'vue'
import {
  moveDay,
  type CalendarDay,
  type DailyProfitCalendar,
} from '../../services/daily-profit-calendar'
import DailyProfitCell from './daily-profit-cell.vue'

const props = defineProps<{
  calendar: DailyProfitCalendar
  selected?: string
  compact?: boolean
}>()

const emit = defineEmits<{ select: [date: string] }>()

const WEEKDAY_LABELS = ['Mon', 'Wed', 'Fri']
const FIRST_DAY_ROW = 2
const KEY_STEPS = new Map([
  ['ArrowUp', -1],
  ['ArrowDown', 1],
  ['ArrowLeft', -7],
  ['ArrowRight', 7],
])

const grid = ref<HTMLElement | null>(null)
const selectedId = useId()

const firstWeekColumn = computed(() => (props.compact ? 1 : 2))

const gridAttributes = computed(() =>
  props.compact
    ? { 'aria-hidden': 'true' as const }
    : {
        role: 'application',
        'aria-label': 'Daily profit calendar',
        tabindex: 0,
        'aria-describedby': selectedId,
      }
)

const placeOf = (index: number) => {
  const position = index + props.calendar.offset
  return {
    gridColumn: Math.floor(position / 7) + firstWeekColumn.value,
    gridRow: (position % 7) + FIRST_DAY_ROW,
  }
}

const isSelectable = (day: CalendarDay) => !props.compact && day.kind !== 'missing'

const cellAttributes = (day: CalendarDay) => {
  if (props.compact) return {}
  const current = day.date === props.selected ? { id: selectedId, 'aria-current': 'true' } : {}
  return { role: 'img', 'aria-label': day.name, title: day.name, ...current }
}

const onClick = (day: CalendarDay) => {
  if (!isSelectable(day)) return
  grid.value?.focus()
  emit('select', day.date)
}

const onKeydown = (event: KeyboardEvent) => {
  const step = KEY_STEPS.get(event.key)
  if (!step || !props.selected || event.altKey || event.ctrlKey || event.metaKey) return
  event.preventDefault()
  const date = moveDay(props.calendar, props.selected, step)
  if (date) emit('select', date)
}
</script>

<style scoped>
.calendar-grid {
  display: grid;
  grid-template-columns: 30px repeat(var(--weeks), minmax(0, 1fr));
  grid-template-rows: auto repeat(7, 18px);
  gap: 3px;
  max-width: calc(30px + var(--weeks) * 21px);
  margin-inline: 2px;
  outline: none;
}

.calendar-grid.compact {
  grid-template-columns: repeat(var(--weeks), minmax(0, 1fr));
  grid-template-rows: none;
  gap: 2px;
  max-width: calc(var(--weeks) * 20px - 2px);
  margin-inline: 0;
}

.month-label,
.weekday-label {
  font-size: var(--text-label);
  line-height: 1;
  color: var(--color-ink-soft);
}

.month-label {
  grid-row: 1;
  padding-bottom: 3px;
  white-space: nowrap;
}

.compact .month-label {
  padding-bottom: 10px;
}

.compact .last-week {
  justify-self: end;
}

.weekday-label {
  grid-column: 1;
  align-self: center;
}

.selectable {
  cursor: pointer;
}

.selected {
  outline: 1.5px solid var(--color-ink);
  outline-offset: 2px;
}

.calendar-grid:focus-visible .selected {
  outline: 2px solid var(--color-brass);
}
</style>
