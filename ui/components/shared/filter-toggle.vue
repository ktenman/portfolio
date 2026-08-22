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

<style scoped>
.dropdown-toggle::after {
  display: inline-block;
  margin-left: 0.255em;
  vertical-align: 0.255em;
  content: '';
  border-top: 0.3em solid;
  border-right: 0.3em solid transparent;
  border-bottom: 0;
  border-left: 0.3em solid transparent;
  transition: transform var(--transition-fast);
}

.dropdown-toggle[aria-expanded='true']::after {
  transform: rotate(180deg);
}
</style>
