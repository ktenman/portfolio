<template>
  <div class="stat-card card-shell">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value">{{ value }}</div>
  </div>
</template>

<script lang="ts" setup>
defineProps<{
  label: string
  value: string
}>()
</script>

<style scoped>
.stat-label {
  margin-bottom: 0.25rem;
  font-size: 0.75rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-ink-muted);
}

.stat-value {
  font-size: var(--text-title);
  font-weight: 550;
  line-height: 1.2;
  color: var(--color-ink);
}
</style>
