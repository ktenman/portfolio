<template>
  <alert-message v-if="!data && isError" :variant="missing ? 'info' : 'danger'">
    {{
      missing
        ? 'The PÖFF programme has not been imported yet.'
        : 'The PÖFF programme could not be loaded.'
    }}
    <button
      type="button"
      class="ml-2 cursor-pointer font-semibold underline"
      data-testid="poff-retry"
      @click="refetch()"
    >
      Retry
    </button>
  </alert-message>
  <template v-else>
    <poff-day-strip v-if="data" v-model="selected" :days="days" :today="today" />
    <div
      class="mb-4 flex flex-wrap items-center gap-x-1.5 gap-y-2"
      role="group"
      aria-label="Filters"
    >
      <button
        v-for="option in CITIES"
        :key="option"
        type="button"
        :class="[CHIP, { active: city === option }]"
        :aria-pressed="city === option"
        @click="city = option"
      >
        {{ option }}
      </button>
      <span class="platform-separator mx-1" aria-hidden="true" />
      <button
        type="button"
        :class="[CHIP, { active: rated }]"
        :aria-pressed="rated"
        @click="rated = !rated"
      >
        IMDb 7+
      </button>
      <button
        type="button"
        :class="[CHIP, { active: available }]"
        :aria-pressed="available"
        @click="available = !available"
      >
        Hide sold out
      </button>
    </div>
    <div class="overflow-hidden rounded-container border border-hairline bg-surface">
      <div
        class="flex items-baseline justify-between bg-surface-band px-4 py-2.5 text-sm text-ink-soft"
      >
        <span data-testid="poff-band">
          <strong class="font-semibold text-ink">{{ formatWeekdayDate(selected) }}</strong>
          · {{ rows.length }} screenings in {{ city }}
        </span>
        <span
          class="text-label font-semibold tracking-wide text-brass-deep uppercase not-md-down:hidden"
        >
          IMDb
        </span>
      </div>
      <div v-if="!data" aria-busy="true" aria-label="Loading the programme">
        <div v-for="i in 6" :key="i" class="flex gap-6 border-t border-hairline px-4 py-3.5">
          <span class="skeleton h-4 w-12" />
          <span class="flex-1 space-y-2">
            <span class="skeleton block h-4 w-2/5" />
            <span class="skeleton block h-3 w-3/5" />
          </span>
        </div>
      </div>
      <p v-else-if="!rows.length" class="border-t border-hairline px-4 py-6 text-ink-soft">
        No screenings in {{ city }} on {{ formatWeekdayDate(selected) }}
      </p>
      <table v-else ref="table" class="w-full md-down:block">
        <thead class="md-down:hidden">
          <tr
            class="border-t border-hairline text-left text-label font-semibold tracking-wide text-brass-deep uppercase"
          >
            <th class="w-32 py-2 pl-4">Time</th>
            <th class="py-2 pr-4">Film</th>
            <th class="w-56 py-2 pr-4">Cinema</th>
            <th class="w-16 py-2 pr-4 text-right">IMDb</th>
            <th class="w-28 py-2 pr-4 text-right">Tickets</th>
          </tr>
        </thead>
        <tbody class="md-down:block">
          <tr v-if="split.earlier.length" class="border-t border-hairline md-down:block">
            <td colspan="5" class="md-down:block">
              <button
                ref="toggle"
                type="button"
                class="flex min-h-11 w-full cursor-pointer items-center justify-between px-4 py-2.5 text-left text-sm text-ink-soft hover:bg-surface-hover hover:text-ink"
                :aria-expanded="earlierOpen"
                data-testid="poff-earlier"
                @click="earlierOpen = !earlierOpen"
              >
                Earlier today · {{ split.earlier.length }} screenings
                <radio-icon name="down" :class="{ 'rotate-180': earlierOpen }" />
              </button>
            </td>
          </tr>
          <poff-screening-row
            v-for="row in earlierOpen ? rows : split.coming"
            :key="row.screening.id"
            :film="row.film"
            :screening="row.screening"
          />
        </tbody>
      </table>
    </div>
  </template>
</template>

<script lang="ts" setup>
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import { useIntervalFn, useNow } from '@vueuse/core'
import { notImported, usePoffCatalogue } from '../../composables/use-poff-catalogue'
import { CITIES, usePoffFilters } from '../../composables/use-poff-filters'
import { formatWeekdayDate } from '../../utils/iso-dates'
import { defaultDay, festivalDays, splitStarted, tallinnDay, timetable } from '../../utils/poff'
import RadioIcon from '../radio/radio-icon.vue'
import AlertMessage from '../shared/alert-message.vue'
import PoffDayStrip from './poff-day-strip.vue'
import PoffScreeningRow from './poff-screening-row.vue'

const CHIP =
  'platform-btn platform-btn-outline relative md-down:h-9 md-down:text-2xs md-down:before:absolute md-down:before:inset-x-0 md-down:before:top-1/2 md-down:before:h-11 md-down:before:-translate-y-1/2'

const { data, isError, error, refetch } = usePoffCatalogue()
const { day, city, rated, available } = usePoffFilters()
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })
const earlierOpen = ref(false)
const table = useTemplateRef<HTMLElement>('table')
const toggle = useTemplateRef<HTMLButtonElement>('toggle')

const missing = computed(() => notImported(error.value))
const today = computed(() => tallinnDay(now.value.toISOString()))
const days = computed(() => festivalDays(data.value?.films ?? []))
const selected = computed({
  get: () =>
    day.value && days.value.includes(day.value) ? day.value : defaultDay(days.value, today.value),
  set: (value: string) => {
    day.value = value === defaultDay(days.value, today.value) ? null : value
  },
})
const rows = computed(() =>
  timetable(data.value?.films ?? [], selected.value, {
    city: city.value,
    rated: rated.value,
    available: available.value,
  })
)
const split = computed(() =>
  selected.value === today.value
    ? splitStarted(rows.value, now.value)
    : { earlier: [], coming: rows.value }
)

watch(
  () => split.value.earlier.length,
  async () => {
    if (!table.value?.contains(document.activeElement)) return
    await nextTick()
    if (!table.value?.contains(document.activeElement)) toggle.value?.focus()
  }
)
</script>
