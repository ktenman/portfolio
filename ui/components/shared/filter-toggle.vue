<template>
  <button
    class="platform-btn dropdown-toggle"
    :class="{ active: active ?? selected !== available }"
    type="button"
    :aria-expanded="open"
    @click="open = !open"
  >
    Filters ({{ selected }}/{{ available }})
  </button>
</template>

<script setup lang="ts">
const open = defineModel<boolean>({ required: true })

withDefaults(
  defineProps<{
    selected: number
    available: number
    active?: boolean
  }>(),
  { active: undefined }
)
</script>
