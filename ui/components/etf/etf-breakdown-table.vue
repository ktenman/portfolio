<template>
  <div class="card shadow-sm border-0">
    <div class="card-body p-0">
      <loading-spinner v-if="isLoading" class="my-5" />
      <div v-else-if="isError" class="alert alert-danger m-4 mb-0">
        <strong>Error:</strong>
        {{ errorMessage }}
      </div>
      <div v-else-if="holdings.length === 0" class="alert alert-info m-4 mb-0">
        <strong>No data found.</strong>
        Make sure you have ETFs with active positions.
      </div>
      <data-table
        v-else
        :items="holdings"
        :columns="columns"
        :is-loading="false"
        :is-error="false"
        empty-message="No holdings data available"
      >
        <template #cell-holdingTicker="{ item }">
          <div class="ticker-cell">
            <img
              v-if="item.holdingTicker"
              :src="utilityService.getLogoUrl(item.holdingTicker)"
              :alt="item.holdingName"
              class="company-logo"
              @error="handleImageError"
            />
            <span class="ticker-symbol">{{ item.holdingTicker || '-' }}</span>
          </div>
        </template>
        <template #footer>
          <tr v-if="holdings.length > 0" class="table-footer-totals">
            <td class="fw-bold ps-3">Total</td>
            <td></td>
            <td class="fw-bold text-end">100.0000%</td>
            <td class="fw-bold text-end">{{ formatCurrency(totalValue) }}</td>
            <td colspan="2"></td>
          </tr>
        </template>
      </data-table>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { EtfHoldingBreakdownDto } from '../../models/generated/domain-models'
import DataTable from '../shared/data-table.vue'
import type { ColumnDefinition } from '../shared/data-table.vue'
import LoadingSpinner from '../shared/loading-spinner.vue'
import { utilityService } from '../../services/utility-service'

const props = defineProps<{
  holdings: EtfHoldingBreakdownDto[]
  isLoading: boolean
  isError: boolean
  errorMessage: string
}>()

const totalValue = computed(() => props.holdings.reduce((sum, h) => sum + h.totalValueEur, 0))

const formatCurrency = (value: number | null) => {
  if (value === null || value === undefined) return '-'
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

const formatPercentage = (value: number | null) => {
  if (value === null || value === undefined) return '-'
  return `${value.toFixed(4)}%`
}

const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.style.display = 'none'
}

const columns: ColumnDefinition[] = [
  {
    key: 'holdingTicker',
    label: 'Ticker',
    sortable: true,
    class: 'fw-semibold',
  },
  {
    key: 'holdingName',
    label: 'Name',
    sortable: true,
  },
  {
    key: 'percentageOfTotal',
    label: '% of Total',
    sortable: true,
    formatter: formatPercentage,
    class: 'text-end',
  },
  {
    key: 'totalValueEur',
    label: 'VALUE',
    sortable: true,
    formatter: formatCurrency,
    class: 'text-end fw-semibold',
  },
  {
    key: 'holdingSector',
    label: 'Sector',
    sortable: true,
    formatter: (value: string | null) => value || '-',
  },
  {
    key: 'inEtfs',
    label: 'Found in ETFs',
    sortable: false,
    class: 'text-muted small',
    formatter: (value: string | null) => {
      if (!value) return '-'
      return value
        .split(',')
        .map(symbol => symbol.trim().split(':')[0])
        .join(', ')
    },
  },
]
</script>

<style scoped>
.ticker-cell {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.company-logo {
  width: 32px;
  height: 32px;
  object-fit: cover;
  flex-shrink: 0;
  border-radius: 50%;
  background-color: #f8f9fa;
  padding: 2px;
}

.ticker-symbol {
  font-weight: 600;
}

.card {
  border-radius: 0.5rem;
  overflow: hidden;
  border: 1px solid #e0e0e0;
}

.card :deep(.table) {
  margin-bottom: 0;
}

.card :deep(.table thead th) {
  background-color: #f8f9fa;
  border-bottom: 2px solid #dee2e6;
  font-weight: 600;
  text-transform: uppercase;
  font-size: 0.75rem;
  letter-spacing: 0.5px;
  color: #6c757d;
  padding: 1rem 0.75rem;
}

.card :deep(.table tbody tr) {
  transition: background-color 0.15s ease;
}

.card :deep(.table tbody tr:hover) {
  background-color: #f8f9fa;
}

.card :deep(.table td) {
  padding: 0.875rem 0.75rem;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
}

.table-footer-totals {
  font-weight: 600;
  background: linear-gradient(to bottom, #f8f9fa 0%, #e9ecef 100%);
  border-top: 2px solid #dee2e6;
}

.table-footer-totals td {
  padding: 1rem 0.75rem;
  font-size: 0.95rem;
}

@media (max-width: 768px) {
  .card :deep(.table th:nth-child(1)),
  .card :deep(.table td:nth-child(1)) {
    display: none;
  }

  .card :deep(.table td:nth-child(2)) {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .card :deep(.table th:nth-child(3)) {
    font-size: 0;
  }

  .card :deep(.table th:nth-child(3))::after {
    content: '%';
    font-size: 0.75rem;
  }

  .card :deep(.table th:nth-child(6)) {
    font-size: 0;
  }

  .card :deep(.table th:nth-child(6))::after {
    content: 'ETF';
    font-size: 0.75rem;
  }

  .card :deep(.table td:nth-child(6)) {
    font-size: 0.75rem;
  }
}

@media (max-width: 768px) and (orientation: portrait) {
  .card :deep(.table th:nth-child(1)),
  .card :deep(.table td:nth-child(1)) {
    display: none !important;
  }
}
</style>
