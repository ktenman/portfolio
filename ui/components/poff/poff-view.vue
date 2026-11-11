<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3">
    <div class="mb-4 flex flex-wrap items-baseline gap-x-3">
      <h2 class="mb-0">PÖFF</h2>
      <p v-if="data" class="mb-0 text-sm text-ink-soft md-down:hidden">
        {{ festivalSummary(data.films) }}
      </p>
    </div>
    <nav class="mb-4 flex gap-7 border-b border-hairline-strong" aria-label="PÖFF views">
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
    <router-view />
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
import { festivalSummary, synced, tallinnDay } from '../../utils/poff'

const VIEWS = [
  { to: '/poff', label: 'Timetable' },
  { to: '/poff/films', label: 'Films' },
]

const { data } = usePoffCatalogue()
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })
const today = computed(() => tallinnDay(now.value.toISOString()))
</script>
