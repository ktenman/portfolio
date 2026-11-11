<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3">
    <div class="mb-4 flex flex-wrap items-baseline gap-x-3">
      <h2 class="mb-0">PÖFF</h2>
      <p v-if="data" class="mb-0 text-sm text-ink-soft md-down:hidden">
        {{ festivalSummary(data.films) }}
      </p>
    </div>
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

const { data } = usePoffCatalogue()
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })
const today = computed(() => tallinnDay(now.value.toISOString()))
</script>
