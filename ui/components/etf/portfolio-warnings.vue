<template>
  <div v-if="warnings.length > 0" class="warnings-card card-shell">
    <h6 class="warnings-title">Portfolio Warnings</h6>
    <div class="warnings-list">
      <div
        v-for="warning in warnings"
        :key="warning.rule"
        class="warning-row"
        :class="{ breached: warning.breached }"
      >
        <span class="warning-marker" aria-hidden="true"></span>
        <span class="sr-only">{{ warning.breached ? 'Warning:' : 'Within limit:' }}</span>
        <span class="warning-label">
          {{ warning.label }}
          <span v-if="warning.detail" class="warning-detail">{{ warning.detail }}</span>
        </span>
        <span class="warning-measured">
          {{ formatRulePercentage(warning, warning.measuredPercentage) }}
        </span>
        <span class="warning-threshold">
          / {{ formatRulePercentage(warning, warning.thresholdPercentage) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {
  PortfolioWarningRule,
  type PortfolioWarningDto,
} from '../../models/generated/domain-models'

defineProps<{
  warnings: PortfolioWarningDto[]
}>()

const formatRulePercentage = (warning: PortfolioWarningDto, value: number): string =>
  `${value.toFixed(warning.rule === PortfolioWarningRule.AVERAGE_TER ? 3 : 2)}%`
</script>

<style scoped>
.warnings-title {
  margin-bottom: 0.75rem;
  font-size: var(--text-label);
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-ink-muted);
}

.warnings-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(19rem, 1fr));
  column-gap: 1.5rem;
}

.warning-row {
  display: grid;
  grid-template-columns: 0.375rem minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 0.5rem;
  padding: 0.3125rem 0;
  border-bottom: 1px solid var(--color-hairline);
}

.warning-marker {
  width: 0.375rem;
  height: 0.375rem;
  border-radius: 50%;
  background-color: var(--color-hairline-strong);
}

.warning-row.breached .warning-marker {
  background-color: var(--color-status-warning);
}

.warning-label {
  font-size: var(--text-base);
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.warning-detail {
  color: var(--color-ink-muted);
}

.warning-detail::before {
  content: '· ';
}

.warning-measured {
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--color-ink);
  font-variant-numeric: tabular-nums;
}

.warning-row.breached .warning-measured {
  color: var(--color-status-warning);
}

.warning-threshold {
  font-size: var(--text-label);
  color: var(--color-ink-soft);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
