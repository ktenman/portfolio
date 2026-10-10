<template>
  <div v-if="calendar && day" class="daily-profit-calendar card-shell p-0 md:p-6">
    <div class="calendar-body hidden md:flex">
      <div class="calendar-main" :style="{ '--weeks': calendar.weeks }">
        <h2 class="calendar-heading">
          Every day since {{ formatDayMonth(calendar.days[0].date) }}
        </h2>
        <p class="calendar-sub">
          Change in total profit per day. Money you add is not counted as a gain.
        </p>
        <daily-profit-grid :calendar="calendar" :selected="day.date" @select="select" />
        <daily-profit-legend />
      </div>
      <div class="calendar-side">
        <daily-profit-panel
          :day="day"
          :previous="moveDay(calendar, day.date, -1)"
          :next="moveDay(calendar, day.date, 1)"
          @select="select"
        />
        <daily-profit-figures :calendar="calendar" @select="select" />
      </div>
    </div>
    <daily-profit-compact class="md:hidden" :calendar="calendar" />
    <p class="sr-only" role="status">{{ announcement }}</p>
  </div>
  <div v-else-if="!rows" class="daily-profit-calendar card-shell p-0 md:p-6" aria-hidden="true">
    <div class="calendar-body hidden md:flex">
      <div class="calendar-main skeleton h-66"></div>
      <div class="calendar-side">
        <div class="skeleton h-36"></div>
        <div class="skeleton h-36"></div>
        <div class="flex flex-wrap gap-2">
          <div class="skeleton h-9.5 w-56"></div>
          <div class="skeleton h-9.5 w-54"></div>
        </div>
      </div>
    </div>
    <div class="skeleton h-93 md:hidden"></div>
  </div>
  <alert-message v-else-if="error" variant="warning">
    Could not load the daily profit calendar: {{ error }}
  </alert-message>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { formatDateToString } from '../../composables/use-quick-dates'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'
import { buildDailyProfitCalendar, dayOn, moveDay } from '../../services/daily-profit-calendar'
import { formatDayMonth } from '../../utils/iso-dates'
import AlertMessage from '../shared/alert-message.vue'
import DailyProfitCompact from './daily-profit-compact.vue'
import DailyProfitFigures from './daily-profit-figures.vue'
import DailyProfitGrid from './daily-profit-grid.vue'
import DailyProfitLegend from './daily-profit-legend.vue'
import DailyProfitPanel from './daily-profit-panel.vue'

const props = defineProps<{ rows: PortfolioSummaryDto[] | undefined; error?: string | null }>()

const selectedDate = ref('')
const announcement = ref('')

const calendar = computed(() =>
  props.rows ? buildDailyProfitCalendar(props.rows, formatDateToString(new Date())) : null
)

const day = computed(() => calendar.value && dayOn(calendar.value, selectedDate.value))

watch(
  calendar,
  current => {
    announcement.value = ''
    const isSelectable = day.value && day.value.kind !== 'missing'
    if (!current || isSelectable) return
    selectedDate.value = (current.best ?? current.days[current.days.length - 1]).date
  },
  { immediate: true }
)

const select = (date: string) => {
  const target = calendar.value && dayOn(calendar.value, date)
  if (!target || date === selectedDate.value) return
  selectedDate.value = date
  announcement.value = target.note ? `${target.name}. ${target.note}` : target.name
}
</script>

<style scoped>
.calendar-body {
  flex-wrap: wrap;
  gap: 28px 44px;
}

.calendar-main {
  flex: 0 1 calc(34px + max(var(--weeks, 27), 27) * 21px);
  min-width: 0;
}

.calendar-side {
  display: flex;
  flex: 1 1 420px;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.calendar-heading {
  margin-bottom: 0.25rem;
  font-size: 1.125rem;
}

.calendar-sub {
  margin-bottom: 1.25rem;
  font-size: var(--text-2xs);
  color: var(--color-ink-soft);
}
</style>
