<template>
  <h2 class="mb-3">Radio</h2>
  <div class="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center">
    <nav class="flex gap-2" aria-label="Radio views">
      <router-link to="/radio" class="platform-btn" v-bind="tab" @click.exact="text = ''">
        Shows
      </router-link>
      <router-link to="/radio/catch-up" class="platform-btn" v-bind="tab" @click.exact="text = ''">
        Catch-up
      </router-link>
    </nav>
    <search-input
      v-model="text"
      :active="!!query"
      placeholder="Search what was said"
      label="Search what was said"
      :maxlength="100"
    />
  </div>
  <radio-search v-if="query" :query="query" />
  <slot v-else />
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { refDebounced } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import SearchInput from '../shared/search-input.vue'
import RadioSearch from './radio-search.vue'

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
