<template>
  <div class="breakdown-card">
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
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 0.5rem;
  padding: 1rem;
  height: 100%;
}

.breakdown-title {
  font-weight: 600;
  margin-bottom: 0.75rem;
  color: #1a1a1a;
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
  border-bottom: 1px solid #f0f0f0;
}

.breakdown-item:last-child {
  border-bottom: none;
}

.breakdown-name {
  font-size: 0.875rem;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 70%;
}

.breakdown-value {
  font-size: 0.875rem;
  font-weight: 500;
  color: #1a1a1a;
}
</style>
