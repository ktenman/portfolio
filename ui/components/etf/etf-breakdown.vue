<template>
  <div class="etf-breakdown-container">
    <div class="mb-4">
      <h2 class="mb-0">ETF Breakdown</h2>
      <div v-if="availableEtfs.length > 0" class="etf-filter-container mt-2">
        <div class="etf-buttons">
          <button
            v-for="etf in availableEtfs"
            :key="etf"
            class="etf-btn"
            :class="{ active: isEtfSelected(etf) }"
            @click="toggleEtf(etf)"
            type="button"
          >
            {{ getSymbolOnly(etf) }}
          </button>
          <span class="etf-separator"></span>
          <button class="etf-btn" @click="toggleAllEtfs" type="button">
            {{ selectedEtfs.length === availableEtfs.length ? 'Clear All' : 'Select All' }}
          </button>
        </div>
      </div>
    </div>

    <etf-breakdown-header
      v-if="!isLoading"
      :total-value="totalValue"
      :unique-holdings="holdings.length"
      :selected-etfs="selectedEtfs"
      :available-etfs="availableEtfs"
    />

    <div v-if="!isLoading && holdings.length > 0" class="charts-section mb-4">
      <div class="row g-3">
        <div class="col-md-6">
          <etf-breakdown-chart title="Top Companies" :chart-data="companyChartData" />
        </div>
        <div class="col-md-6">
          <etf-breakdown-chart title="Sector Allocation" :chart-data="sectorChartData" />
        </div>
      </div>
    </div>

    <etf-breakdown-table
      :holdings="holdings"
      :is-loading="isLoading"
      :is-error="isError"
      :error-message="errorMessage"
    />
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { etfBreakdownService } from '../../services/etf-breakdown-service'
import type { EtfHoldingBreakdownDto } from '../../models/generated/domain-models'
import EtfBreakdownHeader from './etf-breakdown-header.vue'
import EtfBreakdownChart from './etf-breakdown-chart.vue'
import type { ChartDataItem } from './etf-breakdown-chart.vue'
import EtfBreakdownTable from './etf-breakdown-table.vue'

const holdings = ref<EtfHoldingBreakdownDto[]>([])
const allHoldings = ref<EtfHoldingBreakdownDto[]>([])
const isLoading = ref(false)
const isError = ref(false)
const errorMessage = ref('')
const selectedEtfs = useLocalStorage<string[]>('portfolio_selected_etfs', [])

const availableEtfs = computed(() => {
  if (allHoldings.value.length === 0) return []

  const etfSet = new Set<string>()
  allHoldings.value.forEach(holding => {
    holding.inEtfs.split(',').forEach(etf => {
      const trimmedEtf = etf.trim()
      if (trimmedEtf) {
        etfSet.add(trimmedEtf)
      }
    })
  })

  return Array.from(etfSet).sort()
})

watch(
  availableEtfs,
  newEtfs => {
    if (newEtfs.length > 0 && selectedEtfs.value.length === 0) {
      selectedEtfs.value = [...newEtfs]
    } else if (newEtfs.length > 0) {
      const validEtfs = selectedEtfs.value.filter(e => newEtfs.includes(e))
      if (validEtfs.length === 0) {
        selectedEtfs.value = [...newEtfs]
      } else if (validEtfs.length !== selectedEtfs.value.length) {
        selectedEtfs.value = validEtfs
      }
    }
  },
  { immediate: true }
)

const totalValue = computed(() => holdings.value.reduce((sum, h) => sum + h.totalValueEur, 0))

const sectorChartData = computed<ChartDataItem[]>(() => {
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

  const topSectorsCount = 20
  const mainSectors = sortedSectors.slice(0, topSectorsCount)
  const smallSectors = sortedSectors.slice(topSectorsCount)

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

const companyChartData = computed<ChartDataItem[]>(() => {
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

const loadBreakdown = async () => {
  isLoading.value = true
  isError.value = false
  errorMessage.value = ''

  try {
    allHoldings.value = await etfBreakdownService.getBreakdown()

    if (
      selectedEtfs.value.length === 0 ||
      selectedEtfs.value.length === availableEtfs.value.length
    ) {
      holdings.value = allHoldings.value
    } else {
      holdings.value = await etfBreakdownService.getBreakdown(selectedEtfs.value)
    }
  } catch (error) {
    isError.value = true
    errorMessage.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    isLoading.value = false
  }
}

watch(selectedEtfs, async () => {
  if (allHoldings.value.length === 0) return

  isLoading.value = true
  try {
    if (
      selectedEtfs.value.length === 0 ||
      selectedEtfs.value.length === availableEtfs.value.length
    ) {
      holdings.value = allHoldings.value
    } else {
      holdings.value = await etfBreakdownService.getBreakdown(selectedEtfs.value)
    }
  } catch (error) {
    isError.value = true
    errorMessage.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    isLoading.value = false
  }
})

const isEtfSelected = (etf: string): boolean => {
  return selectedEtfs.value.includes(etf)
}

const toggleEtf = (etf: string) => {
  const index = selectedEtfs.value.indexOf(etf)
  if (index > -1) {
    selectedEtfs.value = selectedEtfs.value.filter(e => e !== etf)
  } else {
    selectedEtfs.value = [...selectedEtfs.value, etf]
  }
}

const toggleAllEtfs = () => {
  if (selectedEtfs.value.length === availableEtfs.value.length) {
    selectedEtfs.value = []
  } else {
    selectedEtfs.value = [...availableEtfs.value]
  }
}

const getSymbolOnly = (fullSymbol: string): string => {
  return fullSymbol.split(':')[0]
}

onMounted(async () => {
  await loadBreakdown()
})
</script>

<style scoped>
.etf-breakdown-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 1.5rem;
}

.etf-filter-container {
  display: flex;
  align-items: center;
  padding: 0;
  background: transparent;
}

.etf-buttons {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.375rem;
}

.etf-separator {
  width: 1px;
  height: 1.25rem;
  background-color: #d1d5db;
  display: inline-block;
}

.etf-btn {
  padding: 0.3125rem 0.625rem;
  border: 1px solid #e2e8f0;
  background: white;
  color: #6b7280;
  border-radius: 0.375rem;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.12s ease;
  white-space: nowrap;
}

.etf-btn:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #4b5563;
}

.etf-btn:active {
  background: #f1f5f9;
  transform: scale(0.98);
}

.etf-btn.active {
  background: #4b5563;
  color: white;
  border-color: #4b5563;
  font-weight: 500;
}

.etf-btn.active:hover {
  background: #374151;
  border-color: #374151;
  color: white;
}

@media (max-width: 768px) {
  .etf-breakdown-container {
    padding: 1rem;
  }

  .etf-filter-container {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.375rem;
  }

  .etf-buttons {
    width: 100%;
  }

  .etf-separator {
    display: none;
  }
}

@media (min-width: 769px) {
  .etf-filter-container {
    align-items: center;
  }
}
</style>
