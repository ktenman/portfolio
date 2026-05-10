<template>
  <div
    v-if="currentHoldingsTotal > 0"
    class="rebalance-status-card"
    :class="`status-${summary.cssClass}`"
  >
    <div class="status-icon">{{ summary.icon }}</div>
    <div class="status-message">
      <div class="status-title">{{ summary.title }}</div>
      <div v-if="summary.detail" class="status-detail">{{ summary.detail }}</div>
    </div>
    <button
      type="button"
      class="configure-btn"
      data-testid="configure-thresholds"
      @click="$emit('open-config')"
    >
      Configure thresholds
    </button>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { RebalanceStatus } from '../../models/generated/domain-models'

interface RowSummary {
  status: RebalanceStatus
  symbol: string
}

const props = defineProps<{
  rows: RowSummary[]
  currentHoldingsTotal: number
}>()

defineEmits<{ 'open-config': [] }>()

const pluralize = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`

const summary = computed(() => {
  const drifting: string[] = []
  const rebalance: string[] = []
  for (const row of props.rows) {
    if (row.status === RebalanceStatus.REBALANCE) rebalance.push(row.symbol)
    else if (row.status === RebalanceStatus.DRIFTING) drifting.push(row.symbol)
  }
  if (rebalance.length > 0) {
    const detail =
      drifting.length === 0
        ? rebalance.join(', ')
        : `${rebalance.join(', ')} (rebalance), ${drifting.join(', ')} (drifting)`
    const verb = rebalance.length === 1 ? 'needs' : 'need'
    return {
      cssClass: 'rebalance',
      icon: '⚠',
      title: `${pluralize(rebalance.length, 'holding')} ${verb} rebalancing`,
      detail,
    }
  }
  if (drifting.length > 0) {
    return {
      cssClass: 'drifting',
      icon: '!',
      title: `${pluralize(drifting.length, 'holding')} drifting`,
      detail: drifting.join(', '),
    }
  }
  return {
    cssClass: 'ok',
    icon: '✓',
    title: 'Portfolio within tolerance',
    detail: '',
  }
})
</script>

<style scoped>
.rebalance-status-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  border: 1px solid;
  margin-bottom: 1rem;
}

.rebalance-status-card.status-ok {
  background: #ecfdf5;
  border-color: #6ee7b7;
  color: #065f46;
}

.rebalance-status-card.status-drifting {
  background: #fffbeb;
  border-color: #fcd34d;
  color: #92400e;
}

.rebalance-status-card.status-rebalance {
  background: #fef2f2;
  border-color: #fca5a5;
  color: #991b1b;
}

.status-icon {
  font-size: 1.25rem;
  font-weight: 700;
}

.status-message {
  flex: 1;
}

.status-title {
  font-weight: 600;
  font-size: 0.95rem;
}

.status-detail {
  font-size: 0.8125rem;
  margin-top: 0.125rem;
  opacity: 0.85;
}

.configure-btn {
  background: transparent;
  border: 1px solid currentColor;
  color: inherit;
  padding: 0.25rem 0.625rem;
  border-radius: 0.375rem;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.12s ease;
}

.configure-btn:hover {
  background: rgba(255, 255, 255, 0.4);
}
</style>
