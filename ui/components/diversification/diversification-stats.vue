<template>
  <div class="summary-cards mb-4">
    <div class="row g-3">
      <div class="col-md-3 col-6">
        <div class="stat-card">
          <div class="stat-label">Weighted TER</div>
          <div class="stat-value">{{ formatTer(weightedTer, 3) }}</div>
        </div>
      </div>
      <div class="col-md-3 col-6">
        <div class="stat-card">
          <div class="stat-label">Weighted Return</div>
          <div class="stat-value">{{ formatReturn(weightedAnnualReturn) }}</div>
        </div>
      </div>
      <div class="col-md-3 col-6">
        <div class="stat-card">
          <div class="stat-label">Unique Holdings</div>
          <div class="stat-value">{{ totalUniqueHoldings.toLocaleString() }}</div>
        </div>
      </div>
      <div class="col-md-3 col-6">
        <div class="stat-card">
          <div class="stat-label">Top 10 Concentration</div>
          <div class="stat-value">{{ formatPercentage(top10Percentage) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { useDiversificationFormatters } from '../../composables/use-diversification-formatters'

defineProps<{
  weightedTer: number
  weightedAnnualReturn: number
  totalUniqueHoldings: number
  top10Percentage: number
}>()

const { formatTer, formatReturn, formatPercentage } = useDiversificationFormatters()
</script>

<style scoped>
.stat-card {
  background: white;
  border: 1px solid #e0e0e0;
  padding: 1rem 1.5rem;
  border-radius: 0.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  text-align: center;
}

.stat-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #6c757d;
  font-weight: 500;
  margin-bottom: 0.25rem;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  line-height: 1.2;
  color: #1a1a1a;
}

@media (max-width: 768px) {
  .stat-card {
    padding: 0.75rem;
  }

  .stat-value {
    font-size: 1.25rem;
  }
}
</style>
