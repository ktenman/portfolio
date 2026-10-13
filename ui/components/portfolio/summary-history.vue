<template>
  <div class="summary-history" :aria-busy="busy">
    <div
      v-if="displayInterval === 'monthly' && !monthly.rows"
      class="summary-monthly-placeholder"
      aria-label="Loading monthly history"
    >
      <skeleton-loader type="card" class="mb-2 md:mb-10" />
      <skeleton-loader type="table" :rows="7" :columns="8" />
    </div>
    <template v-else>
      <alert-message
        v-if="displayInterval === 'monthly' && monthly.error"
        variant="warning"
        class="summary-history-notice mb-3"
      >
        Could not refresh monthly history. Showing available data as of
        {{ formatDate(monthly.rows?.[monthly.rows.length - 1]?.date ?? '') }}.
        <button
          type="button"
          class="platform-btn md-down:min-h-11"
          :disabled="monthly.fetching"
          @click="emit('retryHistory')"
        >
          Retry monthly history
        </button>
      </alert-message>
      <daily-profit-calendar
        v-if="displayInterval === 'daily'"
        :key="platforms.join()"
        :rows="daily.rows"
        :error="daily.error"
        :history="daily.history"
        class="mb-2 md:mb-10"
        @load-history="emit('loadHistory')"
      />
      <monthly-profit-calendar
        v-else
        :key="platforms.join()"
        :calendar="monthly.calendar"
        class="mb-2 md:mb-10"
      />
      <summary-history-table
        v-if="displayInterval === 'daily' || monthly.calendar"
        :key="displayInterval"
        :interval="displayInterval"
        :rows="rows"
        :months="monthly.calendar?.months"
      />
      <div v-if="interval === 'daily' && fetching" class="mt-4 text-center">
        <skeleton-loader type="text" :lines="2" />
      </div>
    </template>
    <loading-veil v-if="busy" delayed message="Loading months" />
  </div>
</template>

<script lang="ts" setup>
import type { SummaryInterval } from '../../composables/use-summary-interval'
import type { CalendarHistory } from '../../composables/use-daily-profit-calendar'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'
import type { MonthlyProfit } from '../../services/monthly-profit'
import { formatDate } from '../../utils/formatters'
import AlertMessage from '../shared/alert-message.vue'
import SkeletonLoader from '../shared/skeleton-loader.vue'
import LoadingVeil from '../shared/loading-veil.vue'
import DailyProfitCalendar from './daily-profit-calendar.vue'
import MonthlyProfitCalendar from './monthly-profit-calendar.vue'
import SummaryHistoryTable from './summary-history-table.vue'

defineProps<{
  interval: SummaryInterval
  displayInterval: SummaryInterval
  platforms: string[]
  rows: PortfolioSummaryDto[]
  daily: { rows: PortfolioSummaryDto[] | undefined; error: string | null; history: CalendarHistory }
  monthly: {
    rows: PortfolioSummaryDto[] | undefined
    calendar: MonthlyProfit | null
    error: string | null
    fetching: boolean
  }
  busy: boolean
  fetching: boolean
}>()

const emit = defineEmits<{ loadHistory: []; retryHistory: [] }>()
</script>

<style scoped>
.summary-history {
  position: relative;
}

.summary-history > .loading-veil {
  align-items: flex-start;
  padding-top: 2rem;
}
</style>
