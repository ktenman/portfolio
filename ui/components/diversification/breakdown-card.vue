<template>
  <div class="breakdown-card card-shell">
    <h6 class="breakdown-title">{{ title }}</h6>
    <div class="breakdown-list">
      <div v-for="item in items.slice(0, maxItems)" :key="item.key" class="breakdown-item">
        <span class="breakdown-name">{{ item.name }}</span>
        <span class="breakdown-value">{{ formatPercentage(item.percentage) }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { formatPercentage } from '../../utils/formatters'

interface BreakdownItem {
  key: string
  name: string
  percentage: number
}

withDefaults(
  defineProps<{
    title: string
    items: BreakdownItem[]
    maxItems?: number
  }>(),
  {
    maxItems: 15,
  }
)
</script>

<style scoped>
.breakdown-card {
  height: 100%;
}

.breakdown-title {
  margin-bottom: 0.75rem;
  font-size: var(--text-label);
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-ink-muted);
}

.breakdown-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.breakdown-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.25rem 0;
  border-bottom: 1px solid var(--color-hairline);
}

.breakdown-item:last-child {
  border-bottom: none;
}

.breakdown-name {
  font-size: var(--text-base);
  color: var(--color-ink-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 70%;
}

.breakdown-value {
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--color-ink);
}
</style>
