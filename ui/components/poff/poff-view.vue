<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3">
    <router-view v-slot="{ Component, route }">
      <div class="mb-4 flex flex-wrap items-center gap-x-3 gap-y-3">
        <poff-back-link v-if="route.params.slug && query === null" />
        <template v-else>
          <h2 class="mb-0" :class="{ 'md-down:hidden': query !== null }">PÖFF</h2>
          <p v-if="data" class="mb-0 text-sm text-ink-soft md-down:hidden">
            {{ festivalSummary(data.films) }}
          </p>
        </template>
        <poff-search-field :compact="!!route.params.slug" class="ml-auto md:w-md" />
      </div>
      <poff-search v-if="data && searching" :films="data.films" :query="query ?? ''" :now="now" />
      <template v-else>
        <nav
          v-if="!route.params.slug"
          class="mb-4 flex gap-7 border-b border-hairline-strong"
          aria-label="PÖFF views"
        >
          <router-link
            v-for="view in VIEWS"
            :key="view.to"
            :to="view.to"
            class="relative px-0.5 py-2.5 text-base leading-6 font-medium text-ink-soft transition-colors after:absolute after:inset-x-0 after:-bottom-px after:h-0.5 after:transition-colors hover:text-ink hover:after:bg-hairline-strong [&.active]:font-semibold [&.active]:text-ink [&.active]:after:bg-brass"
            exact-active-class="active"
          >
            {{ view.label }}
          </router-link>
        </nav>
        <component :is="Component" />
      </template>
    </router-view>
    <p v-if="data" class="mt-4 max-w-prose text-sm text-ink-soft">
      Programme from poff.ee, synced {{ synced(data.syncedAt, today) }}. Lighter ratings have under
      100 votes. Information courtesy of IMDb (https://www.imdb.com). Used with permission.
    </p>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useIntervalFn, useNow } from '@vueuse/core'
import { usePoffCatalogue } from '../../composables/use-poff-catalogue'
import { usePoffFilters } from '../../composables/use-poff-filters'
import { festivalSummary, synced, tallinnDay } from '../../utils/poff'
import PoffBackLink from './poff-back-link.vue'
import PoffSearch from './poff-search.vue'
import PoffSearchField from './poff-search-field.vue'

const VIEWS = [
  { to: '/poff', label: 'Timetable' },
  { to: '/poff/films', label: 'Films' },
]

const { data } = usePoffCatalogue()
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })
const today = computed(() => tallinnDay(now.value.toISOString()))
const { query } = usePoffFilters()
const searching = computed(() => (query.value?.trim().length ?? 0) >= 2)
</script>
