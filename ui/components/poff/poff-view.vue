<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3">
    <router-view v-slot="{ Component, route }">
      <div
        class="flex flex-wrap items-center gap-x-3 gap-y-3"
        :class="route.params.slug ? 'mb-4' : 'mb-2'"
      >
        <template v-if="route.params.slug">
          <poff-back-link v-if="query === null" />
          <poff-search-field compact class="ml-auto md:w-md" />
        </template>
        <template v-else>
          <h2 class="mb-0">PÖFF</h2>
          <p v-if="data" class="mb-0 text-sm text-ink-soft md-down:hidden">
            {{ festivalSummary(data.films) }}
          </p>
        </template>
      </div>
      <template v-if="!route.params.slug">
        <nav class="mb-4 flex gap-7 border-b border-hairline-strong" aria-label="PÖFF views">
          <router-link
            v-for="view in VIEWS"
            :key="view.to"
            :to="{ path: view.to, query: carried }"
            class="relative min-w-11 px-0.5 py-2.5 text-center text-base leading-6 font-medium text-ink-soft transition-colors after:absolute after:inset-x-0 after:-bottom-px after:h-0.5 after:transition-colors hover:text-ink hover:after:bg-hairline-strong [&.active]:font-semibold [&.active]:text-ink [&.active]:after:bg-brass"
            :exact-active-class="searching ? '' : 'active'"
            :aria-current-value="searching ? 'false' : 'page'"
          >
            {{ view.label }}
          </router-link>
        </nav>
        <poff-search-field class="mb-4 md:w-80" />
      </template>
      <keep-alive exclude="poff-film">
        <poff-search v-if="data && searching" :films="data.films" :query="query ?? ''" :now="now" />
        <component :is="Component" v-else :key="route.name" />
      </keep-alive>
    </router-view>
    <p v-if="data" class="mt-4 max-w-prose text-sm text-ink-soft">
      Programme from poff.ee, synced {{ synced(data.syncedAt, today) }}. Lighter ratings have under
      100 votes. Information courtesy of IMDb (https://www.imdb.com). Used with permission.
    </p>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useIntervalFn, useNow } from '@vueuse/core'
import { usePoffCatalogue } from '../../composables/use-poff-catalogue'
import { SHARED_FILTERS, usePoffFilters } from '../../composables/use-poff-filters'
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
const searching = computed(() => !!data.value && (query.value?.trim().length ?? 0) >= 2)
const route = useRoute()
const carried = computed(() =>
  Object.fromEntries(
    SHARED_FILTERS.filter(key => key in route.query).map(key => [key, route.query[key]])
  )
)
</script>
