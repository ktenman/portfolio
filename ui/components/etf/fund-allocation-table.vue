<template>
  <table class="fund-table">
    <thead>
      <tr>
        <th>Fund</th>
        <th class="num">Weight</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="row.isin" :class="row.status">
        <td>
          <span class="fund-name">{{ row.name }}</span>
          <span v-if="row.status !== 'held'" class="fund-status">{{ row.status }}</span>
          <small class="fund-isin">{{ row.isin }}</small>
        </td>
        <td class="num">{{ row.weight === null ? '—' : formatPercentage(row.weight) }}</td>
      </tr>
      <tr class="cash">
        <td><span class="fund-name">Cash</span></td>
        <td class="num">{{ formatPercentage(cash) }}</td>
      </tr>
    </tbody>
  </table>
</template>

<script lang="ts" setup>
import { formatPercentage } from '../../utils/formatters'
import type { FundRow } from '../../services/fund-allocation'

defineProps<{
  rows: FundRow[]
  cash: number
}>()
</script>

<style scoped>
.fund-table {
  width: 100%;
  margin-top: 1.25rem;
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.fund-table th {
  padding: 0.5rem 0.625rem;
  border-bottom: 1px solid var(--color-hairline-strong);
  font-size: var(--text-label);
  font-weight: 500;
  text-align: left;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-ink-faint);
}

.fund-table td {
  padding: 0.5rem 0.625rem;
  border-bottom: 1px solid var(--color-hairline);
  vertical-align: top;
}

.fund-table .num {
  text-align: right;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.fund-isin {
  display: block;
  color: var(--color-ink-faint);
  font-size: var(--text-label);
}

.fund-status {
  margin-left: 0.5rem;
  padding: 0 0.375rem;
  border-radius: var(--radius-control);
  font-size: var(--text-label);
  background: var(--color-brass-wash);
  color: var(--color-brass-deep);
}

.dropped td {
  color: var(--color-ink-faint);
}

.dropped .fund-name {
  text-decoration: line-through;
}
</style>
