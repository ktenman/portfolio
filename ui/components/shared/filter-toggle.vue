<template>
  <button
    class="platform-btn dropdown-toggle"
    :class="{ active: selected !== available }"
    type="button"
    :aria-expanded="open"
    @click="open = !open"
  >
    Filters ({{ selected }}/{{ available }})
  </button>
</template>

<script setup lang="ts">
const open = defineModel<boolean>({ required: true })

defineProps<{
  selected: number
  available: number
}>()
</script>
