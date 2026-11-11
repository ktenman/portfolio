<template>
  <div class="mb-4 flex gap-1.5" role="group" aria-label="Results">
    <button
      v-for="(label, key) in GROUPS"
      :key="key"
      type="button"
      :class="[CHIP, 'gap-1.5', { active: group === key }]"
      :aria-pressed="group === key"
      @click="group = key"
    >
      {{ label }}
      <span class="text-ink-soft">{{ totals[key] }}</span>
    </button>
  </div>
  <p v-if="!totals.all" class="py-6 text-ink-soft">Nothing matches “{{ query }}”</p>
  <div v-else class="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,30rem)]">
    <section
      v-if="films.length && group !== 'people'"
      :class="[BOX, { 'lg:col-span-2': single }]"
      data-testid="poff-search-films"
    >
      <h3 :class="BAND">
        <span>
          <strong class="font-semibold">Films</strong>
          · {{ films.length }}
        </span>
        <span :class="LABEL" class="lg:hidden">IMDb</span>
      </h3>
      <table class="w-full md-down:block">
        <thead class="md-down:hidden">
          <tr :class="LABEL" class="border-t border-hairline text-left">
            <th class="py-2 pl-4">Film</th>
            <th class="w-80 py-2 pr-4">Next screening</th>
            <th class="w-16 py-2 pr-4 text-right">IMDb</th>
          </tr>
        </thead>
        <tbody class="md-down:block">
          <tr
            v-for="film in shown(films, 'films')"
            :key="film.id"
            class="relative grid grid-cols-[minmax(0,1fr)_auto] gap-x-4 border-t border-hairline px-4 py-3 align-top hover:bg-surface-hover md:table-row md:p-0"
          >
            <td class="md:py-3 md:pl-4">
              <router-link
                :to="{ name: 'PoffFilm', params: { slug: film.slug } }"
                class="font-semibold after:absolute after:inset-0 hover:underline focus-visible:underline"
                data-testid="poff-search-film"
              >
                <poff-highlight :text="film.title" :query="query" />
              </router-link>
              <p v-if="filmLine(film)" class="mb-0 text-sm text-ink-soft">
                <poff-highlight :text="filmLine(film)" :query="query" />
              </p>
            </td>
            <td class="col-start-1 pt-1 text-sm md:py-3 md:pr-4">
              <radio-badge v-if="soldOut(film, now)" tone="error">Sold out</radio-badge>
              <template v-else>{{ nextLine(film, now) }}</template>
            </td>
            <td class="col-start-2 row-start-1 text-right md:py-3 md:pr-4">
              <poff-rating :imdb="film.imdb" />
            </td>
          </tr>
        </tbody>
      </table>
      <poff-search-fold
        v-if="more.films"
        :label="count(more.films, 'more film')"
        @open="open.films = true"
      />
    </section>
    <section v-if="people.length && group !== 'films'" :class="[BOX, { 'lg:col-span-2': single }]">
      <h3 :class="BAND">
        <span>
          <strong class="font-semibold">People</strong>
          · {{ people.length }}
        </span>
      </h3>
      <ul class="mb-0" data-testid="poff-search-people">
        <li
          v-for="person in shown(people, 'people')"
          :key="person.name"
          class="border-t border-hairline"
        >
          <router-link
            :to="{ name: 'PoffFilms', query: { person: person.name, showing: '0' } }"
            class="flex min-h-11 items-center justify-between gap-3 px-4 py-2.5 hover:bg-surface-hover"
            data-testid="poff-search-person"
          >
            <span class="min-w-0">
              <span class="block font-semibold">
                <poff-highlight :text="person.name" :query="query" />
              </span>
              <span class="block text-sm text-ink-soft">
                {{ person.role }} · {{ person.film.englishTitle || person.film.title }}
              </span>
            </span>
            <radio-icon name="left" class="size-4 shrink-0 rotate-180 text-ink-soft" />
          </router-link>
        </li>
      </ul>
      <poff-search-fold
        v-if="more.people"
        :label="`${more.people} more ${more.people === 1 ? 'person' : 'people'}`"
        @open="open.people = true"
      />
    </section>
  </div>
</template>

<script lang="ts" setup>
import { computed, reactive, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import type { PoffFilm } from '../../models/generated/domain-models'
import { count } from '../../utils/formatters'
import { CHIP, LABEL, filmLine } from '../../utils/poff'
import { nextLine, soldOut } from '../../utils/poff-films'
import { searchFilms, searchPeople } from '../../utils/poff-search'
import RadioBadge from '../radio/radio-badge.vue'
import RadioIcon from '../radio/radio-icon.vue'
import PoffHighlight from './poff-highlight.vue'
import PoffRating from './poff-rating.vue'
import PoffSearchFold from './poff-search-fold.vue'

const props = defineProps<{ films: PoffFilm[]; query: string; now: Date }>()

type Group = 'all' | 'films' | 'people'

const GROUPS: Record<Group, string> = { all: 'All', films: 'Films', people: 'People' }
const BOX = 'overflow-hidden rounded-container border border-hairline bg-surface'
const BAND =
  'mb-0 flex items-baseline justify-between bg-surface-band px-4 py-2.5 text-base font-normal text-ink-soft [&_strong]:text-ink'

const group = ref<Group>('all')
const open = reactive({ films: false, people: false })
const wide = useMediaQuery('(min-width: 768px)')

const films = computed(() => searchFilms(props.films, props.query, props.now))
const people = computed(() => searchPeople(props.films, props.query))
const totals = computed(() => ({
  all: films.value.length + people.value.length,
  films: films.value.length,
  people: people.value.length,
}))

const limit = (key: 'films' | 'people') =>
  group.value !== 'all' || open[key] ? Infinity : wide.value ? 8 : 3
const shown = <T,>(list: T[], key: 'films' | 'people') => list.slice(0, limit(key))
const hidden = (list: unknown[], key: 'films' | 'people') => Math.max(0, list.length - limit(key))
const more = computed(() => ({
  films: hidden(films.value, 'films'),
  people: hidden(people.value, 'people'),
}))
const single = computed(() => group.value !== 'all' || !films.value.length || !people.value.length)

watch(
  () => props.query,
  () => Object.assign(open, { films: false, people: false })
)
</script>
