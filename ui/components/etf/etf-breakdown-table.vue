<template>
  <div class="card shadow-sm border-0">
    <div class="card-body p-0">
      <loading-spinner v-if="isLoading" class="my-5" />
      <div v-else-if="isError" class="alert alert-danger m-4 mb-0">
        <strong>Error:</strong>
        {{ errorMessage }}
      </div>
      <div v-else-if="holdings.length === 0" class="empty-state">
        <div class="empty-state-icon">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="48"
            height="48"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.3-4.3" />
            <path d="M11 8v6" />
            <path d="M8 11h6" />
          </svg>
        </div>
        <h5 class="empty-state-title">{{ emptyStateMessage.title }}</h5>
        <p class="empty-state-subtitle">{{ emptyStateMessage.subtitle }}</p>
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
import { formatPlatformName } from '../../utils/platform-utils'

const props = defineProps<{
  holdings: EtfHoldingBreakdownDto[]
  isLoading: boolean
  isError: boolean
  errorMessage: string
  selectedEtfs?: string[]
  selectedPlatforms?: string[]
  masterHoldings?: EtfHoldingBreakdownDto[]
}>()

const totalValue = computed(() => props.holdings.reduce((sum, h) => sum + h.totalValueEur, 0))

const emptyStateMessage = computed(() => {
  if (
    !props.selectedEtfs?.length ||
    !props.selectedPlatforms?.length ||
    !props.masterHoldings?.length
  ) {
    return { title: 'No data found', subtitle: 'Make sure you have ETFs with active positions.' }
  }
  const selectedEtfSymbols = props.selectedEtfs.map(e => e.split(':')[0])
  const etfPlatformMap = new Map<string, Set<string>>()
  props.masterHoldings.forEach(holding => {
    if (holding.platforms) {
      holding.inEtfs.split(',').forEach(etf => {
        const etfSymbol = etf.trim().split(':')[0]
        if (!etfPlatformMap.has(etfSymbol)) {
          etfPlatformMap.set(etfSymbol, new Set())
        }
        holding.platforms.split(',').forEach(p => {
          etfPlatformMap.get(etfSymbol)?.add(p.trim())
        })
      })
    }
  })
  const unavailableEtfs: { etf: string; availablePlatforms: string[] }[] = []
  selectedEtfSymbols.forEach(etf => {
    const etfPlatforms = etfPlatformMap.get(etf)
    if (etfPlatforms) {
      const hasOverlap = props.selectedPlatforms?.some(p => etfPlatforms.has(p))
      if (!hasOverlap) {
        unavailableEtfs.push({
          etf,
          availablePlatforms: Array.from(etfPlatforms),
        })
      }
    }
  })
  if (unavailableEtfs.length > 0) {
    const etfList = unavailableEtfs
      .map(({ etf, availablePlatforms }) => {
        const platformNames = availablePlatforms.map(p => formatPlatformName(p)).join(', ')
        return `${etf} (available on ${platformNames})`
      })
      .join(', ')
    return {
      title: 'No matching holdings',
      subtitle: `The selected ETFs are not available on the selected platforms: ${etfList}`,
    }
  }
  return { title: 'No data found', subtitle: 'Try selecting different ETFs or platforms.' }
})

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
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 2rem;
  text-align: center;
}

.empty-state-icon {
  color: #9ca3af;
  margin-bottom: 1rem;
}

.empty-state-title {
  color: #374151;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.empty-state-subtitle {
  color: #6b7280;
  font-size: 0.9rem;
  max-width: 400px;
  margin: 0;
  line-height: 1.5;
}

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
