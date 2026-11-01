<template>
  <h2>Radio</h2>
  <nav class="mb-4 flex gap-7 border-b border-hairline-strong" aria-label="Radio views">
    <router-link
      v-for="view in VIEWS"
      :key="view.to"
      :to="{ path: view.to, query: { station: station?.toLowerCase() } }"
      class="relative px-0.5 py-2.5 text-base leading-6 font-medium text-ink-soft transition-colors after:absolute after:inset-x-0 after:-bottom-px after:h-0.5 after:transition-colors hover:text-ink hover:after:bg-hairline-strong [&.active]:font-semibold [&.active]:text-ink [&.active]:after:bg-brass"
      v-bind="tab"
      @click.exact="text = ''"
    >
      {{ view.label }}
    </router-link>
  </nav>
  <div class="mb-6 flex gap-x-4 gap-y-3 max-md:flex-col md:items-center">
    <search-input
      v-model="text"
      :active="!!query"
      placeholder="Search what was said"
      label="Search what was said"
      :maxlength="100"
    />
    <div class="flex gap-1.5" role="group" aria-label="Station">
      <button
        v-for="chip in CHIPS"
        :key="chip ?? 'all'"
        type="button"
        class="platform-btn relative max-md:before:absolute max-md:before:inset-x-0 max-md:before:top-1/2 max-md:before:h-11 max-md:before:-translate-y-1/2"
        :class="{ active: station === chip }"
        :aria-pressed="station === chip"
        @click="station = chip"
      >
        {{ chip ? STATIONS[chip] : 'All' }}
      </button>
    </div>
  </div>
  <radio-search v-if="query" :query="query" />
  <slot v-else />
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { refDebounced } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { useStationFilter } from '../../composables/use-radio-recordings'
import { Station } from '../../models/generated/domain-models'
import { STATIONS } from '../../utils/radio'
import SearchInput from '../shared/search-input.vue'
import RadioSearch from './radio-search.vue'

const VIEWS = [
  { to: '/radio', label: 'Recordings' },
  { to: '/radio/shows', label: 'Shows' },
  { to: '/radio/catch-up', label: 'Catch-up' },
]
const CHIPS = [null, ...Object.values(Station)]

const route = useRoute()
const router = useRouter()
const station = useStationFilter()
const query = computed(() => (typeof route.query.q === 'string' ? route.query.q.trim() : ''))
const tab = computed(() =>
  query.value
    ? { exactActiveClass: '', ariaCurrentValue: 'false' as const }
    : { exactActiveClass: 'active' }
)
const text = ref(query.value)
const typed = refDebounced(
  computed(() => text.value.trim()),
  300
)

watch(typed, q => router.replace({ query: { ...route.query, q: q || undefined } }))
watch(query, q => {
  if (q !== text.value.trim()) text.value = q
})
</script>
