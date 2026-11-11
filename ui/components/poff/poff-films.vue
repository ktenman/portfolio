<template>
  <poff-load-error v-if="!data && isError" :error="error" :refetch="refetch" />
  <template v-else>
    <poff-filter-bar
      v-model:day="filters.day"
      :films="data?.films ?? []"
      :days="days"
      :today="today"
      :switches="['rated', 'available', 'showing']"
      clearable
    >
      <div class="-mt-2 mb-3 flex items-center justify-between gap-4">
        <span class="text-ink-soft" data-testid="poff-count">
          <strong class="font-semibold text-ink">{{ count(list.length, 'film') }}</strong>
          {{ filters.showing ? 'still showing' : '' }}
        </span>
        <select
          v-model="filters.sort"
          class="min-h-11 cursor-pointer bg-transparent font-medium"
          aria-label="Sort films"
          data-testid="poff-sort"
        >
          <option v-for="(label, key) in SORTS" :key="key" :value="key">{{ label }}</option>
        </select>
      </div>
    </poff-filter-bar>
    <ul v-if="!data" :class="GRID" aria-busy="true" aria-label="Loading the films">
      <li v-for="i in 8" :key="i">
        <span class="skeleton block aspect-[3/2] rounded-container" />
        <span class="skeleton mt-3 block h-4 w-3/5" />
        <span class="skeleton mt-2 block h-3 w-4/5" />
      </li>
    </ul>
    <p v-else-if="!list.length" class="py-6 text-ink-soft">No films match these filters</p>
    <ul v-else :class="GRID">
      <poff-film-card v-for="film in list" :key="film.id" :film="film" :now="now" />
    </ul>
  </template>
</template>

<script lang="ts" setup>
import { computed, reactive } from 'vue'
import { useIntervalFn, useNow } from '@vueuse/core'
import { usePoffCatalogue } from '../../composables/use-poff-catalogue'
import { usePoffFilters } from '../../composables/use-poff-filters'
import { count } from '../../utils/formatters'
import { festivalDays, tallinnDay } from '../../utils/poff'
import { SORTS, filterFilms, sortFilms } from '../../utils/poff-films'
import PoffFilmCard from './poff-film-card.vue'
import PoffFilterBar from './poff-filter-bar.vue'
import PoffLoadError from './poff-load-error.vue'

const GRID = 'grid grid-cols-2 gap-x-4 gap-y-6 md:grid-cols-4'

const { data, isError, error, refetch } = usePoffCatalogue()
const filters = reactive(usePoffFilters())
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })

const today = computed(() => tallinnDay(now.value.toISOString()))
const days = computed(() => festivalDays(data.value?.films ?? []))
const day = computed(() => (filters.day && days.value.includes(filters.day) ? filters.day : null))
const list = computed(() =>
  sortFilms(
    filterFilms(data.value?.films ?? [], { ...filters, day: day.value }, now.value),
    filters.sort,
    now.value
  )
)
</script>
