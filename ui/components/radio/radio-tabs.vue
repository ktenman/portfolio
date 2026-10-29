<template>
  <h2 class="mb-2">Radio</h2>
  <nav class="mb-4 flex gap-7 border-b border-hairline-strong" aria-label="Radio views">
    <router-link
      v-for="view in VIEWS"
      :key="view.to"
      :to="view.to"
      class="relative px-0.5 py-2.5 text-base leading-6 font-medium text-ink-soft transition-colors after:absolute after:inset-x-0 after:-bottom-px after:h-0.5 after:transition-colors hover:text-ink hover:after:bg-hairline-strong [&.active]:font-semibold [&.active]:text-ink [&.active]:after:bg-brass"
      v-bind="tab"
      @click.exact="text = ''"
    >
      {{ view.label }}
    </router-link>
  </nav>
  <search-input
    v-model="text"
    class="mb-6"
    :active="!!query"
    placeholder="Search what was said"
    label="Search what was said"
    :maxlength="100"
  />
  <radio-search v-if="query" :query="query" />
  <slot v-else />
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { refDebounced } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import SearchInput from '../shared/search-input.vue'
import RadioSearch from './radio-search.vue'

const VIEWS = [
  { to: '/radio', label: 'Recordings' },
  { to: '/radio/shows', label: 'Shows' },
  { to: '/radio/catch-up', label: 'Catch-up' },
]

const route = useRoute()
const router = useRouter()
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
