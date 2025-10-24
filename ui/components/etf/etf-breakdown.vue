<template>
  <div class="etf-breakdown-container">
    <etf-breakdown-header
      v-if="!isLoading"
      :total-value="totalValue"
      :unique-holdings="holdings.length"
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
import { ref, onMounted, computed } from 'vue'
import { etfBreakdownService } from '../../services/etf-breakdown-service'
import type { EtfHoldingBreakdownDto } from '../../models/generated/domain-models'
import EtfBreakdownHeader from './etf-breakdown-header.vue'
import EtfBreakdownChart from './etf-breakdown-chart.vue'
import type { ChartDataItem } from './etf-breakdown-chart.vue'
import EtfBreakdownTable from './etf-breakdown-table.vue'

const holdings = ref<EtfHoldingBreakdownDto[]>([])
const isLoading = ref(false)
const isError = ref(false)
const errorMessage = ref('')

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
    holdings.value = await etfBreakdownService.getBreakdown()
  } catch (error) {
    isError.value = true
    errorMessage.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    isLoading.value = false
  }
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

@media (max-width: 768px) {
  .etf-breakdown-container {
    padding: 1rem;
  }
}
</style>
