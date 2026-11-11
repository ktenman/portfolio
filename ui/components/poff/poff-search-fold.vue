<template>
  <button
    type="button"
    class="flex min-h-11 w-full cursor-pointer items-center justify-between border-t border-hairline px-4 py-2.5 text-left text-ink-soft hover:bg-surface-hover hover:text-ink"
    aria-expanded="false"
    data-testid="poff-search-more"
    @click="emit('open')"
  >
    {{ label }}
    <radio-icon name="down" />
  </button>
</template>

<script lang="ts" setup>
import RadioIcon from '../radio/radio-icon.vue'

defineProps<{ label: string }>()
const emit = defineEmits<{ open: [] }>()
</script>
