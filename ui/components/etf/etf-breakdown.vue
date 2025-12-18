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
      <div v-if="availablePlatforms.length > 1" class="platform-filter-container mt-2">
        <div class="platform-buttons">
          <button
            v-for="platform in availablePlatforms"
            :key="platform"
            class="platform-btn"
            :class="{ active: isPlatformSelected(platform) }"
            @click="togglePlatform(platform)"
            type="button"
          >
            {{ formatPlatformName(platform) }}
          </button>
          <span class="platform-separator"></span>
          <button class="platform-btn" @click="toggleAllPlatforms" type="button">
            {{
              selectedPlatforms.length === availablePlatforms.length ? 'Clear All' : 'Select All'
            }}
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
          <etf-breakdown-chart title="Sector Allocation" :chart-data="sectorChartData" />
        </div>
        <div class="col-md-6">
          <etf-breakdown-chart title="Top Companies" :chart-data="companyChartData" />
        </div>
      </div>
    </div>

    <etf-breakdown-table
      :holdings="holdings"
      :is-loading="isLoading"
      :is-error="isError"
      :error-message="errorMessage"
      :selected-etfs="selectedEtfs"
      :selected-platforms="selectedPlatforms"
      :master-holdings="masterHoldings"
    />
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useLocalStorage, useDebounceFn } from '@vueuse/core'
import { etfBreakdownService } from '../../services/etf-breakdown-service'
import {
  buildSectorChartData,
  buildCompanyChartData,
  getFilterParam,
  type ChartDataItem,
} from '../../services/etf-chart-service'
import type { EtfHoldingBreakdownDto } from '../../models/generated/domain-models'
import EtfBreakdownHeader from './etf-breakdown-header.vue'
import EtfBreakdownChart from './etf-breakdown-chart.vue'
import EtfBreakdownTable from './etf-breakdown-table.vue'
import { formatPlatformName } from '../../utils/platform-utils'

const holdings = ref<EtfHoldingBreakdownDto[]>([])
const allHoldings = ref<EtfHoldingBreakdownDto[]>([])
const masterHoldings = ref<EtfHoldingBreakdownDto[]>([])
const isLoading = ref(false)
const isError = ref(false)
const errorMessage = ref('')
const selectedEtfs = useLocalStorage<string[]>('portfolio_selected_etfs', [])
const selectedPlatforms = useLocalStorage<string[]>('portfolio_etf_breakdown_platforms', [])

const availablePlatforms = computed(() => {
  if (masterHoldings.value.length === 0) return []
  const platformSet = new Set<string>()
  masterHoldings.value.forEach(holding => {
    if (holding.platforms) {
      holding.platforms.split(',').forEach(p => {
        const trimmed = p.trim()
        if (trimmed) platformSet.add(trimmed)
      })
    }
  })
  return Array.from(platformSet).sort()
})

const availableEtfs = computed(() => {
  if (masterHoldings.value.length === 0) return []

  const etfSet = new Set<string>()
  masterHoldings.value.forEach(holding => {
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

watch(
  availablePlatforms,
  newPlatforms => {
    if (newPlatforms.length > 0 && selectedPlatforms.value.length === 0) {
      selectedPlatforms.value = [...newPlatforms]
    } else if (newPlatforms.length > 0) {
      const validPlatforms = selectedPlatforms.value.filter(p => newPlatforms.includes(p))
      if (validPlatforms.length === 0) {
        selectedPlatforms.value = [...newPlatforms]
      } else if (validPlatforms.length !== selectedPlatforms.value.length) {
        selectedPlatforms.value = validPlatforms
      }
    }
  },
  { immediate: true }
)

const totalValue = computed(() => holdings.value.reduce((sum, h) => sum + h.totalValueEur, 0))

const sectorChartData = computed<ChartDataItem[]>(() => buildSectorChartData(holdings.value))

const companyChartData = computed<ChartDataItem[]>(() => buildCompanyChartData(holdings.value))

const getEtfsParam = (): string[] | undefined =>
  getFilterParam(selectedEtfs.value, availableEtfs.value)

const getPlatformsParam = (): string[] | undefined =>
  getFilterParam(selectedPlatforms.value, availablePlatforms.value)

const loadBreakdown = async (refreshMaster = false) => {
  isLoading.value = true
  isError.value = false
  errorMessage.value = ''

  try {
    if (refreshMaster || masterHoldings.value.length === 0) {
      masterHoldings.value = await etfBreakdownService.getBreakdown(undefined, undefined)
    }
    allHoldings.value = await etfBreakdownService.getBreakdown(undefined, getPlatformsParam())

    const etfsParam = getEtfsParam()
    if (!etfsParam) {
      holdings.value = allHoldings.value
    } else {
      holdings.value = await etfBreakdownService.getBreakdown(etfsParam, getPlatformsParam())
    }
  } catch (error) {
    isError.value = true
    errorMessage.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    isLoading.value = false
  }
}

const updateFilteredHoldings = async () => {
  if (allHoldings.value.length === 0) return
  isLoading.value = true
  try {
    const etfsParam = getEtfsParam()
    if (!etfsParam) {
      holdings.value = allHoldings.value
    } else {
      holdings.value = await etfBreakdownService.getBreakdown(etfsParam, getPlatformsParam())
    }
  } catch (error) {
    isError.value = true
    errorMessage.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    isLoading.value = false
  }
}

const debouncedLoadBreakdown = useDebounceFn(() => loadBreakdown(), 300)

watch(selectedEtfs, updateFilteredHoldings)

watch(selectedPlatforms, debouncedLoadBreakdown)

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

const isPlatformSelected = (platform: string): boolean => {
  return selectedPlatforms.value.includes(platform)
}

const togglePlatform = (platform: string) => {
  const index = selectedPlatforms.value.indexOf(platform)
  if (index > -1) {
    selectedPlatforms.value = selectedPlatforms.value.filter(p => p !== platform)
  } else {
    selectedPlatforms.value = [...selectedPlatforms.value, platform]
  }
}

const toggleAllPlatforms = () => {
  if (selectedPlatforms.value.length === availablePlatforms.value.length) {
    selectedPlatforms.value = []
  } else {
    selectedPlatforms.value = [...availablePlatforms.value]
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

.platform-filter-container {
  display: flex;
  align-items: center;
  padding: 0;
  background: transparent;
}

.platform-buttons {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.375rem;
}

.platform-separator {
  width: 1px;
  height: 1.25rem;
  background-color: #d1d5db;
  display: inline-block;
}

.platform-btn {
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

.platform-btn:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #4b5563;
}

.platform-btn:active {
  background: #f1f5f9;
  transform: scale(0.98);
}

.platform-btn.active {
  background: #0072b2;
  color: white;
  border-color: #0072b2;
  font-weight: 500;
}

.platform-btn.active:hover {
  background: #005a8c;
  border-color: #005a8c;
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

  .platform-filter-container {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.375rem;
  }

  .platform-buttons {
    width: 100%;
  }

  .platform-separator {
    display: none;
  }
}

@media (min-width: 769px) {
  .etf-filter-container {
    align-items: center;
  }
}
</style>
