<template>
  <div v-if="calendar && day" class="daily-profit-calendar card-shell p-0 md:p-6">
    <div class="calendar-body hidden md:flex">
      <div class="calendar-main" :style="{ '--weeks': calendar.weeks }">
        <h2 class="calendar-heading">
          Daily profit since {{ formatDayMonth(calendar.days[0].date) }}
        </h2>
        <daily-profit-grid
          :calendar="calendar"
          :selected="day.date"
          :previewed="previewDate"
          @select="select"
          @preview="previewDate = $event"
        />
        <daily-profit-legend :missing="calendar.days.some(entry => entry.kind === 'missing')" />
      </div>
      <div class="calendar-side">
        <daily-profit-panel
          class="calendar-day"
          :day="preview ?? day"
          :previous="moveDay(calendar, day.date, -1)"
          :next="moveDay(calendar, day.date, 1)"
          @select="select"
        />
        <daily-profit-figures class="calendar-period" :calendar="calendar" @select="select" />
      </div>
    </div>
    <daily-profit-compact class="md:hidden" :calendar="calendar" />
    <p class="sr-only" role="status">{{ announcement }}</p>
  </div>
  <div v-else-if="!rows" class="daily-profit-calendar card-shell p-0 md:p-6" aria-hidden="true">
    <div class="calendar-body hidden md:flex">
      <div class="calendar-main skeleton min-h-[calc(133px+7*var(--cell))]"></div>
      <div class="calendar-side">
        <div class="calendar-day skeleton h-22 @min-[800px]:h-27.5"></div>
        <div class="calendar-period">
          <div
            class="skeleton h-23.5 md-down:h-27 @min-[945px]:@max-[1057px]:h-11.5 @min-[1057px]:h-38"
          ></div>
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
const previewDate = ref<string | null>(null)
const announcement = ref('')

const calendar = computed(() =>
  props.rows ? buildDailyProfitCalendar(props.rows, formatDateToString(new Date())) : null
)

const day = computed(() => calendar.value && dayOn(calendar.value, selectedDate.value))

const preview = computed(() =>
  calendar.value && previewDate.value ? dayOn(calendar.value, previewDate.value) : undefined
)

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
  previewDate.value = null
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
  container: calendar / inline-size;
}

.calendar-main {
  --columns: max(var(--weeks, 27), 27);
  --beside: 0px;
  --cell: clamp(
    18px - max(var(--weeks, 27) - 27, 0) * 1px,
    round(down, (100cqw - var(--beside) - 34px) / var(--columns) - 3px, 1px),
    24px
  );
  --mark: calc(var(--cell) - 2 * round(var(--cell) * 0.2778, 1px));

  display: flex;
  flex: 0 1 calc(34px + var(--columns) * (var(--cell) + 3px));
  flex-direction: column;
  min-width: 0;
}

.calendar-side {
  display: flex;
  flex: 1 1 100%;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.calendar-period {
  padding-top: 14px;
  border-top: 1px solid var(--color-hairline);
}

.calendar-heading {
  padding-bottom: 1.25rem;
  margin-bottom: auto;
  font-size: 1.125rem;
}

@container (min-width: 800px) and (max-width: 944.98px) {
  .calendar-side {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 412px;
    gap: 0 44px;
    padding-top: 20px;
    border-top: 1px solid var(--color-hairline);
  }

  .calendar-period {
    align-self: start;
    padding-top: 0;
    border-top: 0;
  }
}

@container (min-width: 945px) and (max-width: 1056.98px) {
  .calendar-main {
    --beside: calc(44px + 300px);
  }

  .calendar-side {
    display: contents;
  }

  .calendar-day {
    flex: 1 1 0;
  }

  .calendar-period {
    flex: 1 1 100%;
  }
}

@container (min-width: 1057px) {
  .calendar-main {
    --beside: calc(44px + 412px);
  }

  .calendar-side {
    flex-basis: 0;
  }

  .calendar-period {
    margin-top: auto;
  }
}
</style>
