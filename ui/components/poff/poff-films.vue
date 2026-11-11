<template>
  <poff-load-error v-if="!data && isError" :error="error" :refetch="refetch" />
  <template v-else>
    <div class="mb-4 flex items-center gap-x-4 gap-y-3 md-down:flex-col md-down:items-stretch">
      <div
        class="flex gap-1.5 md-down:-mx-3 md-down:overflow-x-auto md-down:px-3 md-down:py-1 md-down:[scrollbar-width:none]"
        role="group"
        aria-label="Filters"
      >
        <button
          v-for="chip in chips"
          :key="chip.label"
          type="button"
          :class="[CHIP, 'shrink-0', { active: chip.on }]"
          :aria-pressed="chip.on"
          @click="chip.toggle()"
        >
          {{ chip.label }}
        </button>
        <button
          type="button"
          :class="[CHIP, 'shrink-0 gap-1', { active: otherGenre }]"
          :aria-expanded="genresOpen"
          @click="genresOpen = !genresOpen"
        >
          {{ otherGenre || 'All genres' }}
          <radio-icon name="down" class="size-3.5" :class="{ 'rotate-180': genresOpen }" />
        </button>
      </div>
      <div class="flex items-center justify-between gap-4 md:ml-auto md:shrink-0">
        <span class="text-ink-soft" data-testid="poff-count">
          <strong class="font-semibold text-ink">{{ filmCount(list.length) }}</strong>
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
    </div>
    <div v-if="genresOpen" class="mb-4 flex flex-wrap gap-1.5" role="group" aria-label="Genres">
      <button
        v-for="name in allGenres"
        :key="name"
        type="button"
        :class="[CHIP, { active: filters.genre === name }]"
        :aria-pressed="filters.genre === name"
        @click="choose(name)"
      >
        {{ name }}
      </button>
    </div>
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
import { computed, reactive, ref } from 'vue'
import { useIntervalFn, useNow } from '@vueuse/core'
import { usePoffCatalogue } from '../../composables/use-poff-catalogue'
import { usePoffFilmFilters } from '../../composables/use-poff-filters'
import { CHIP } from '../../utils/poff'
import {
  SORTS,
  TOP_GENRES,
  filmCount,
  filterFilms,
  genres,
  sortFilms,
} from '../../utils/poff-films'
import RadioIcon from '../radio/radio-icon.vue'
import PoffFilmCard from './poff-film-card.vue'
import PoffLoadError from './poff-load-error.vue'

const GRID = 'grid grid-cols-2 gap-x-4 gap-y-6 md:grid-cols-4'

const { data, isError, error, refetch } = usePoffCatalogue()
const filters = reactive(usePoffFilmFilters())
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })

const allGenres = computed(() => genres(data.value?.films ?? []))
const genresOpen = ref(false)
const otherGenre = computed(() =>
  filters.genre && !TOP_GENRES.includes(filters.genre) ? filters.genre : ''
)
const pick = (name: string) => (filters.genre = filters.genre === name ? null : name)
const choose = (name: string) => {
  pick(name)
  genresOpen.value = false
}
const SWITCHES = [
  ['showing', 'Still showing'],
  ['rated', 'IMDb 7+'],
  ['tonight', 'Tonight'],
] as const
const chips = computed(() => [
  ...SWITCHES.map(([key, label]) => ({
    label,
    on: filters[key],
    toggle: () => (filters[key] = !filters[key]),
  })),
  ...TOP_GENRES.map(name => ({
    label: name,
    on: filters.genre === name,
    toggle: () => pick(name),
  })),
  ...(['programme', 'person'] as const)
    .filter(key => filters[key])
    .map(key => ({ label: `${filters[key]} ×`, on: true, toggle: () => (filters[key] = null) })),
])
const list = computed(() =>
  sortFilms(filterFilms(data.value?.films ?? [], filters, now.value), filters.sort, now.value)
)
</script>
