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
      :unique-holdings="filteredHoldings.length"
      :selected-etfs="selectedEtfs"
      :available-etfs="availableEtfs"
    />

    <div v-if="!isLoading && filteredHoldings.length > 0" class="charts-section mb-4">
      <div class="row g-3">
        <div class="col-lg-4 col-md-6">
          <etf-breakdown-chart title="Sector Allocation" :chart-data="sectorChartData" />
        </div>
        <div class="col-lg-4 col-md-6">
          <etf-breakdown-chart title="Top Companies" :chart-data="companyChartData" />
        </div>
        <div class="col-lg-4 col-md-6">
          <etf-breakdown-chart title="Country Allocation" :chart-data="countryChartData" />
        </div>
      </div>
    </div>

    <div class="search-container mb-3">
      <div class="search-input-wrapper">
        <input
          v-model="searchQuery"
          type="text"
          class="search-input"
          placeholder="Search by name, ticker, sector, or country..."
        />
        <button
          v-if="searchQuery.trim()"
          class="search-clear-btn"
          @click="clearSearch"
          type="button"
          aria-label="Clear search"
        >
          &times;
        </button>
      </div>
      <span v-if="searchQuery.trim() && !isLoading" class="search-results-count">
        {{ filteredHoldings.length }} of {{ holdings.length }} holdings
      </span>
    </div>

    <etf-breakdown-table
      :holdings="filteredHoldings"
      :is-loading="isLoading"
      :is-error="isError"
      :error-message="errorMessage"
      :selected-etfs="selectedEtfs"
      :selected-platforms="selectedPlatforms"
      :master-holdings="masterHoldings"
      :search-query="searchQuery"
    />
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useLocalStorage, useDebounceFn, refDebounced } from '@vueuse/core'
import { etfBreakdownService } from '../../services/etf-breakdown-service'
import { logoService } from '../../services/logo-service'
import {
  buildSectorChartData,
  buildCompanyChartData,
  buildCountryChartData,
  getFilterParam,
  type ChartDataItem,
} from '../../services/etf-chart-service'
import type { EtfHoldingBreakdownDto } from '../../models/generated/domain-models'
import EtfBreakdownHeader from './etf-breakdown-header.vue'
import EtfBreakdownChart from './etf-breakdown-chart.vue'
import EtfBreakdownTable from './etf-breakdown-table.vue'
import { formatPlatformName } from '../../utils/platform-utils'

const holdings = ref<EtfHoldingBreakdownDto[]>([])
const masterHoldings = ref<EtfHoldingBreakdownDto[]>([])
const isLoading = ref(false)
const isError = ref(false)
const errorMessage = ref('')
const selectedEtfs = useLocalStorage<string[]>('portfolio_selected_etfs', [])
const selectedPlatforms = useLocalStorage<string[]>('portfolio_etf_breakdown_platforms', [])
const searchQuery = useLocalStorage<string>('portfolio_etf_search', '')
const debouncedSearchQuery = refDebounced(searchQuery, 200)
const availableEtfs = ref<string[]>([])
const availablePlatforms = ref<string[]>([])

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

const filteredHoldings = computed(() => {
  const query = (debouncedSearchQuery.value ?? '').toLowerCase().trim()
  if (!query) return holdings.value
  return holdings.value.filter(
    h =>
      h.holdingName.toLowerCase().includes(query) ||
      h.holdingTicker?.toLowerCase().includes(query) ||
      h.holdingSector?.toLowerCase().includes(query) ||
      h.holdingCountryName?.toLowerCase().includes(query)
  )
})

const totalValue = computed(() =>
  filteredHoldings.value.reduce((sum, h) => sum + h.totalValueEur, 0)
)

const sectorChartData = computed<ChartDataItem[]>(() =>
  buildSectorChartData(filteredHoldings.value)
)

const companyChartData = computed<ChartDataItem[]>(() =>
  buildCompanyChartData(filteredHoldings.value)
)

const countryChartData = computed<ChartDataItem[]>(() =>
  buildCountryChartData(filteredHoldings.value)
)

const getEtfsParam = (): string[] | undefined =>
  getFilterParam(selectedEtfs.value, availableEtfs.value)

const getPlatformsParam = (): string[] | undefined =>
  getFilterParam(selectedPlatforms.value, availablePlatforms.value)

const refreshAvailableEtfs = async () => {
  const data = await etfBreakdownService.getAvailableEtfs(getPlatformsParam())
  availableEtfs.value = data.etfSymbols
}

const loadBreakdown = async () => {
  isLoading.value = true
  isError.value = false
  errorMessage.value = ''
  try {
    const needsMaster = masterHoldings.value.length === 0
    const platformsParam = getPlatformsParam()
    const etfsParam = getEtfsParam()
    if (needsMaster) {
      const [master, filtered] = await Promise.all([
        etfBreakdownService.getBreakdown(undefined, undefined),
        etfBreakdownService.getBreakdown(etfsParam, platformsParam),
      ])
      masterHoldings.value = master
      holdings.value = filtered
    } else {
      holdings.value = await etfBreakdownService.getBreakdown(etfsParam, platformsParam)
    }
  } catch (error) {
    isError.value = true
    errorMessage.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    isLoading.value = false
  }
}

const debouncedLoadBreakdown = useDebounceFn(() => loadBreakdown(), 300)

const onPlatformChange = useDebounceFn(async () => {
  await refreshAvailableEtfs()
  await loadBreakdown()
}, 300)

watch(selectedEtfs, debouncedLoadBreakdown)

watch(selectedPlatforms, onPlatformChange)

const isEtfSelected = (etf: string): boolean => selectedEtfs.value.includes(etf)

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

const getSymbolOnly = (fullSymbol: string): string => fullSymbol.split(':')[0]

const clearSearch = () => {
  searchQuery.value = ''
}

const isPlatformSelected = (platform: string): boolean => selectedPlatforms.value.includes(platform)

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

const prefetchLogoCandidates = () => {
  const uuids = masterHoldings.value
    .map(h => h.holdingUuid)
    .filter((uuid): uuid is string => uuid !== null)
  if (uuids.length > 0) {
    logoService.prefetchCandidates(uuids)
  }
}

onMounted(async () => {
  const initialData = await etfBreakdownService.getAvailableEtfs()
  availableEtfs.value = initialData.etfSymbols
  availablePlatforms.value = initialData.platforms
  await loadBreakdown()
  prefetchLogoCandidates()
})
</script>

<style scoped>
.etf-breakdown-container {
  max-width: min(1350px, 91vw);
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

.search-container {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.search-input-wrapper {
  position: relative;
  flex: 1;
  max-width: 320px;
}

.search-input {
  width: 100%;
  padding: 0.375rem 2rem 0.375rem 0.75rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  color: #374151;
  background: white;
  transition: border-color 0.15s ease;
}

.search-input:focus {
  outline: none;
  border-color: #4b5563;
}

.search-input::placeholder {
  color: #9ca3af;
}

.search-clear-btn {
  position: absolute;
  right: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: #9ca3af;
  font-size: 1.25rem;
  line-height: 1;
  cursor: pointer;
  padding: 0.125rem 0.25rem;
}

.search-clear-btn:hover {
  color: #4b5563;
}

.search-results-count {
  font-size: 0.75rem;
  color: #6b7280;
  white-space: nowrap;
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

  .search-container {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.375rem;
  }

  .search-input-wrapper {
    width: 100%;
    max-width: none;
  }
}

@media (min-width: 769px) {
  .etf-filter-container {
    align-items: center;
  }
}
</style>
