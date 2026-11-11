<template>
  <poff-load-error v-if="!data && isError" :error="error" :refetch="refetch" />
  <template v-else>
    <poff-day-strip v-model="selected" :days="days" :today="today" />
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
      <span class="mx-1 h-5 w-px bg-hairline-strong" aria-hidden="true" />
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
    <div :class="{ 'grid grid-cols-[minmax(0,1fr)_26rem] items-start gap-6': shown }">
      <div class="overflow-hidden rounded-container border border-hairline bg-surface">
        <div
          class="flex items-baseline justify-between bg-surface-band px-4 py-2.5 text-sm text-ink-soft"
        >
          <span data-testid="poff-band">
            <template v-if="data">
              <strong class="font-semibold text-ink">{{ formatWeekdayDate(selected) }}</strong>
              · {{ count(rows.length, 'screening') }}
              <span class="md-down:hidden">in {{ city }}</span>
            </template>
            <span v-else class="skeleton inline-block h-4 w-40 align-middle" />
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
          <template v-if="rated || available">match the filters</template>
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
          <tbody class="md-down:block" @click="pick">
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
                  Earlier today · {{ count(split.earlier.length, 'screening') }}
                  <radio-icon name="down" :class="{ 'rotate-180': earlierOpen }" />
                </button>
              </td>
            </tr>
            <poff-screening-row
              v-for="row in earlierOpen ? rows : split.coming"
              :key="row.screening.id"
              :film="row.film"
              :screening="row.screening"
              :panel="wide"
              :selected="row.film.slug === shown?.slug"
            />
          </tbody>
        </table>
      </div>
      <poff-film-panel v-if="shown" ref="panel" :film="shown" :now="now" @close="close" />
    </div>
  </template>
</template>

<script lang="ts" setup>
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import { onKeyStroke, useIntervalFn, useMediaQuery, useNow } from '@vueuse/core'
import { usePoffCatalogue } from '../../composables/use-poff-catalogue'
import { CITIES, usePoffFilters } from '../../composables/use-poff-filters'
import { formatWeekdayDate } from '../../utils/iso-dates'
import { count } from '../../utils/formatters'
import {
  CHIP,
  defaultDay,
  festivalDays,
  splitStarted,
  tallinnDay,
  timetable,
} from '../../utils/poff'
import RadioIcon from '../radio/radio-icon.vue'
import PoffDayStrip from './poff-day-strip.vue'
import PoffFilmPanel from './poff-film-panel.vue'
import PoffLoadError from './poff-load-error.vue'
import PoffScreeningRow from './poff-screening-row.vue'

const { data, isError, error, refetch } = usePoffCatalogue()
const { day, film, city, rated, available } = usePoffFilters()
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })
const earlierOpen = ref(false)
const table = useTemplateRef<HTMLElement>('table')
const toggle = useTemplateRef<HTMLButtonElement>('toggle')
const panel = useTemplateRef<InstanceType<typeof PoffFilmPanel>>('panel')
const wide = useMediaQuery('(min-width: 1200px)')
let focus: (() => void) | undefined

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
const shown = computed(() =>
  wide.value ? data.value?.films.find(f => f.slug === film.value) : undefined
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

const pick = (event: MouseEvent) => {
  const target = event.target as Element
  const slug = target.closest<HTMLElement>('[data-film]')?.dataset.film
  if (slug && !target.closest('a[target]')) open(slug)
}

const open = (slug: string) => {
  if (!wide.value) return
  if (slug === shown.value?.slug) return panel.value?.focus()
  focus = () => panel.value?.focus()
  film.value = slug
}

const close = () => {
  const slug = shown.value?.slug
  focus = () =>
    table.value
      ?.querySelector<HTMLElement>(`[data-film="${slug}"] [data-testid="poff-title"]`)
      ?.focus()
  film.value = null
}

watch(
  () => shown.value?.slug,
  () => {
    focus?.()
    focus = undefined
  },
  { flush: 'post' }
)

onKeyStroke('Escape', event => {
  if (shown.value && !(event.target as Element).closest?.('dialog')) close()
})
</script>
