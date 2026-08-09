<template>
  <div class="platform-filter-container">
    <div class="platform-buttons">
      <button
        v-for="platform in available"
        :key="platform"
        class="platform-btn"
        :class="{ active: selected.includes(platform) }"
        type="button"
        @click="emit('toggle', platform)"
      >
        {{ formatPlatformName(platform) }}
      </button>
      <span class="platform-separator"></span>
      <button class="platform-btn" type="button" @click="emit('toggle-all')">
        {{ selected.length === available.length ? 'Clear All' : 'Select All' }}
      </button>
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { formatPlatformName } from '../../utils/platform-utils'

defineProps<{
  available: string[]
  selected: string[]
}>()

const emit = defineEmits<{
  toggle: [platform: string]
  'toggle-all': []
}>()
</script>
