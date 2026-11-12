<template>
  <div
    class="mb-4 flex gap-1.5 md:flex-wrap md-down:-mx-3 md-down:overflow-x-auto md-down:px-3 md-down:py-1.5 md-down:[scrollbar-width:none]"
    role="group"
    aria-label="Filters"
  >
    <template v-for="(group, g) in groups" :key="g">
      <span
        v-if="g"
        class="mx-1 h-5 w-px shrink-0 self-center bg-hairline-strong"
        aria-hidden="true"
      />
      <button
        v-for="chip in group"
        :key="chip.label"
        type="button"
        :class="[CHIP, 'shrink-0', { active: chip.on }]"
        :aria-pressed="chip.on"
        @click="chip.toggle()"
      >
        {{ chip.label }}
      </button>
    </template>
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
</template>

<script lang="ts" setup>
import { computed, reactive, ref } from 'vue'
import type { PoffFilm } from '../../models/generated/domain-models'
import { CITIES, usePoffFilters } from '../../composables/use-poff-filters'
import { CHIP, FILTER_LABELS } from '../../utils/poff'
import { TOP_GENRES, genres } from '../../utils/poff-films'
import RadioIcon from '../radio/radio-icon.vue'

const props = defineProps<{
  films: PoffFilm[]
  switches: (keyof typeof FILTER_LABELS)[]
}>()

const filters = reactive(usePoffFilters())
const genresOpen = ref(false)
const allGenres = computed(() => genres(props.films))
const otherGenre = computed(() =>
  filters.genre && !TOP_GENRES.includes(filters.genre) ? filters.genre : ''
)

const pick = (name: string) => (filters.genre = filters.genre === name ? null : name)
const choose = (name: string) => {
  pick(name)
  genresOpen.value = false
}

const groups = computed(() => [
  CITIES.map(name => ({
    label: name,
    on: filters.city === name,
    toggle: () => (filters.city = name),
  })),
  props.switches.map(key => ({
    label: FILTER_LABELS[key],
    on: filters[key],
    toggle: () => (filters[key] = !filters[key]),
  })),
  [
    ...TOP_GENRES.map(name => ({
      label: name,
      on: filters.genre === name,
      toggle: () => pick(name),
    })),
    ...(['programme', 'person'] as const)
      .filter(key => filters[key])
      .map(key => ({ label: `${filters[key]} ×`, on: true, toggle: () => (filters[key] = null) })),
  ],
])
</script>
