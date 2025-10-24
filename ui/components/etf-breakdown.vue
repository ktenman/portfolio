<template>
  <div class="etf-breakdown-container">
    <div class="page-header mb-4">
      <div>
        <h2 class="page-title mb-2">ETF Holdings Breakdown</h2>
        <p class="page-subtitle text-muted">
          Aggregated view of your underlying holdings across all ETF positions
        </p>
      </div>
      <div v-if="!isLoading && holdings.length > 0" class="header-right">
        <div class="stat-card">
          <div class="stat-label">Total Value</div>
          <div class="stat-value">{{ formatCurrency(totalValue) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Unique Holdings</div>
          <div class="stat-value">{{ holdings.length }}</div>
        </div>
      </div>
    </div>

    <div v-if="!isLoading && holdings.length > 0" class="charts-section mb-4">
      <div class="row g-3">
        <div class="col-md-6">
          <div class="card shadow-sm border-0">
            <div class="card-body p-4">
              <h5 class="chart-title mb-3">Top Companies</h5>
              <div class="chart-container">
                <canvas ref="companyChartCanvas"></canvas>
              </div>
              <div class="chart-legend mt-3">
                <div v-for="item in companyChartData" :key="item.label" class="legend-item">
                  <span class="legend-color" :style="{ backgroundColor: item.color }"></span>
                  <span class="legend-label">{{ item.label }}</span>
                  <span class="legend-value">{{ item.percentage }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="col-md-6">
          <div class="card shadow-sm border-0">
            <div class="card-body p-4">
              <h5 class="chart-title mb-3">Sector Allocation</h5>
              <div class="chart-container">
                <canvas ref="sectorChartCanvas"></canvas>
              </div>
              <div class="chart-legend mt-3">
                <div v-for="item in sectorChartData" :key="item.label" class="legend-item">
                  <span class="legend-color" :style="{ backgroundColor: item.color }"></span>
                  <span class="legend-label">{{ item.label }}</span>
                  <span class="legend-value">{{ item.percentage }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

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
          <template #footer>
            <tr v-if="holdings.length > 0" class="table-footer-totals">
              <td class="fw-bold ps-3">Total</td>
              <td></td>
              <td class="fw-bold text-end">100.00%</td>
              <td class="fw-bold text-end">{{ formatCurrency(totalValue) }}</td>
              <td colspan="2"></td>
            </tr>
          </template>
        </data-table>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { Chart, ArcElement, Tooltip, Legend } from 'chart.js'
import { etfBreakdownService } from '../services/etf-breakdown-service'
import type { EtfHoldingBreakdownDto } from '../models/generated/domain-models'
import DataTable from './shared/data-table.vue'
import type { ColumnDefinition } from './shared/data-table.vue'
import LoadingSpinner from './shared/loading-spinner.vue'

Chart.register(ArcElement, Tooltip, Legend)

const holdings = ref<EtfHoldingBreakdownDto[]>([])
const isLoading = ref(false)
const isError = ref(false)
const errorMessage = ref('')
const sectorChartCanvas = ref<HTMLCanvasElement | null>(null)
const companyChartCanvas = ref<HTMLCanvasElement | null>(null)
let sectorChart: Chart | null = null
let companyChart: Chart | null = null

const totalValue = computed(() => holdings.value.reduce((sum, h) => sum + h.totalValueEur, 0))

const sectorChartData = computed(() => {
  const sectorTotals = new Map<string, number>()

  holdings.value.forEach(holding => {
    const sector = holding.holdingSector || 'Unknown'
    const percentage = holding.percentageOfTotal
    sectorTotals.set(sector, (sectorTotals.get(sector) || 0) + percentage)
  })

  const sortedSectors = Array.from(sectorTotals.entries())
    .sort((a, b) => b[1] - a[1])
    .map(([label, value]) => ({
      label,
      value,
      percentage: value.toFixed(2),
    }))

  const threshold = 2
  const mainSectors = sortedSectors.filter(s => s.value >= threshold)
  const smallSectors = sortedSectors.filter(s => s.value < threshold)

  const result = [...mainSectors]

  if (smallSectors.length > 0) {
    const othersTotal = smallSectors.reduce((sum, s) => sum + s.value, 0)
    result.push({
      label: 'Others',
      value: othersTotal,
      percentage: othersTotal.toFixed(2),
    })
  }

  const colors = [
    '#0072B2',
    '#E69F00',
    '#009E73',
    '#D55E00',
    '#56B4E9',
    '#CC79A7',
    '#F0E442',
    '#7570B3',
    '#1B9E77',
    '#999999',
  ]

  return result.map((item, index) => ({
    ...item,
    color: item.label === 'Others' ? '#999999' : colors[index % colors.length],
  }))
})

const companyChartData = computed(() => {
  const sortedHoldings = [...holdings.value].sort(
    (a, b) => b.percentageOfTotal - a.percentageOfTotal
  )

  const threshold = 2
  const mainHoldings = sortedHoldings.filter(h => h.percentageOfTotal >= threshold)
  const smallHoldings = sortedHoldings.filter(h => h.percentageOfTotal < threshold)

  const result = mainHoldings.map(h => ({
    label: h.holdingName,
    value: h.percentageOfTotal,
    percentage: h.percentageOfTotal.toFixed(2),
  }))

  if (smallHoldings.length > 0) {
    const othersTotal = smallHoldings.reduce((sum, h) => sum + h.percentageOfTotal, 0)
    result.push({
      label: 'Others',
      value: othersTotal,
      percentage: othersTotal.toFixed(2),
    })
  }

  const colors = [
    '#0072B2',
    '#E69F00',
    '#009E73',
    '#D55E00',
    '#56B4E9',
    '#CC79A7',
    '#F0E442',
    '#7570B3',
    '#1B9E77',
    '#999999',
  ]

  return result.map((item, index) => ({
    ...item,
    color: item.label === 'Others' ? '#999999' : colors[index % colors.length],
  }))
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
  return `${value.toFixed(2)}%`
}

const columns: ColumnDefinition[] = [
  {
    key: 'holdingTicker',
    label: 'Ticker',
    sortable: true,
    formatter: (value: string | null) => value || '-',
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

const loadBreakdown = async () => {
  isLoading.value = true
  isError.value = false
  errorMessage.value = ''

  try {
    holdings.value = await etfBreakdownService.getBreakdown()
  } catch (error) {
    isError.value = true
    errorMessage.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    isLoading.value = false
  }
}

const renderSectorChart = () => {
  if (!sectorChartCanvas.value) return

  if (sectorChart) {
    sectorChart.destroy()
  }

  sectorChart = new Chart(sectorChartCanvas.value, {
    type: 'pie',
    data: {
      labels: sectorChartData.value.map(item => item.label),
      datasets: [
        {
          data: sectorChartData.value.map(item => item.value),
          backgroundColor: sectorChartData.value.map(item => item.color),
          borderWidth: 2,
          borderColor: '#ffffff',
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: true,
      plugins: {
        legend: {
          display: false,
        },
        tooltip: {
          callbacks: {
            label: context => {
              const label = context.label || ''
              const value = context.parsed || 0
              return `${label}: ${value.toFixed(2)}%`
            },
          },
        },
      },
    },
  })
}

const renderCompanyChart = () => {
  if (!companyChartCanvas.value) return

  if (companyChart) {
    companyChart.destroy()
  }

  companyChart = new Chart(companyChartCanvas.value, {
    type: 'pie',
    data: {
      labels: companyChartData.value.map(item => item.label),
      datasets: [
        {
          data: companyChartData.value.map(item => item.value),
          backgroundColor: companyChartData.value.map(item => item.color),
          borderWidth: 2,
          borderColor: '#ffffff',
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: true,
      plugins: {
        legend: {
          display: false,
        },
        tooltip: {
          callbacks: {
            label: context => {
              const label = context.label || ''
              const value = context.parsed || 0
              return `${label}: ${value.toFixed(2)}%`
            },
          },
        },
      },
    },
  })
}

onMounted(async () => {
  await loadBreakdown()
  await nextTick()
  renderSectorChart()
  renderCompanyChart()
})
</script>

<style scoped>
.etf-breakdown-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 1.5rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 2rem;
  flex-wrap: wrap;
}

.page-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
}

.page-subtitle {
  font-size: 0.95rem;
  margin: 0;
}

.header-right {
  display: flex;
  flex-direction: row;
  gap: 0.75rem;
}

.stat-card {
  background: white;
  border: 1px solid #e0e0e0;
  padding: 1rem 1.5rem;
  border-radius: 0.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
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

.card {
  border-radius: 0.5rem;
  overflow: hidden;
  border: 1px solid #e0e0e0;
}

.etf-breakdown-container :deep(.table) {
  margin-bottom: 0;
}

.etf-breakdown-container :deep(.table thead th) {
  background-color: #f8f9fa;
  border-bottom: 2px solid #dee2e6;
  font-weight: 600;
  text-transform: uppercase;
  font-size: 0.75rem;
  letter-spacing: 0.5px;
  color: #6c757d;
  padding: 1rem 0.75rem;
}

.etf-breakdown-container :deep(.table tbody tr) {
  transition: background-color 0.15s ease;
}

.etf-breakdown-container :deep(.table tbody tr:hover) {
  background-color: #f8f9fa;
}

.etf-breakdown-container :deep(.table td) {
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

.chart-container {
  position: relative;
  height: 350px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
}

.chart-legend {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-height: 400px;
  overflow-y: auto;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
}

.legend-color {
  width: 12px;
  height: 12px;
  border-radius: 2px;
  flex-shrink: 0;
}

.legend-label {
  flex: 1;
  color: #495057;
}

.legend-value {
  font-weight: 600;
  color: #1a1a1a;
  margin-left: auto;
}

@media (max-width: 768px) {
  .etf-breakdown-container {
    padding: 1rem;
  }

  .page-header {
    flex-direction: column;
    gap: 1rem;
  }

  .header-right {
    width: 100%;
  }

  .stat-card {
    padding: 0.75rem 1rem;
  }

  .stat-value {
    font-size: 1.25rem;
  }

  .page-title {
    font-size: 1.5rem;
  }

  .chart-container {
    height: 250px;
  }

  .etf-breakdown-container :deep(.table th:nth-child(1)),
  .etf-breakdown-container :deep(.table td:nth-child(1)) {
    display: none;
  }

  .etf-breakdown-container :deep(.table td:nth-child(2)) {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .etf-breakdown-container :deep(.table th:nth-child(3)) {
    font-size: 0;
  }

  .etf-breakdown-container :deep(.table th:nth-child(3))::after {
    content: '%';
    font-size: 0.75rem;
  }

  .etf-breakdown-container :deep(.table th:nth-child(6)) {
    font-size: 0;
  }

  .etf-breakdown-container :deep(.table th:nth-child(6))::after {
    content: 'ETF';
    font-size: 0.75rem;
  }

  .etf-breakdown-container :deep(.table td:nth-child(6)) {
    font-size: 0.75rem;
  }
}

@media (max-width: 768px) and (orientation: portrait) {
  .etf-breakdown-container :deep(.table th:nth-child(1)),
  .etf-breakdown-container :deep(.table td:nth-child(1)) {
    display: none !important;
  }
}

@media (max-width: 926px) and (orientation: landscape) {
  .stat-card {
    padding: 1.25rem 2rem;
    min-width: 200px;
  }

  .stat-label {
    font-size: 0.8rem;
  }

  .stat-value {
    font-size: 1.5rem;
  }

  .page-title {
    font-size: 1.6rem;
  }
}
</style>
