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
            <currency-flag :currency="symbolToFundCurrency.get(etf)" :size="14" />
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
      :currency-split="currencySplit"
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
import { usePlatformFilter } from '../../composables/use-platform-filter'
import { etfBreakdownService } from '../../services/etf-breakdown-service'
import { instrumentsService } from '../../services/instruments-service'
import { logoService } from '../../services/logo-service'
import {
  buildSectorChartData,
  buildCompanyChartData,
  buildCountryChartData,
  getFilterParam,
  type ChartDataItem,
} from '../../services/etf-chart-service'
import type { EtfHoldingBreakdownDto, InstrumentDto } from '../../models/generated/domain-models'
import EtfBreakdownHeader from './etf-breakdown-header.vue'
import EtfBreakdownChart from './etf-breakdown-chart.vue'
import EtfBreakdownTable from './etf-breakdown-table.vue'
import CurrencyFlag from '../shared/currency-flag.vue'
import { formatPlatformName } from '../../utils/platform-utils'

const holdings = ref<EtfHoldingBreakdownDto[]>([])
const masterHoldings = ref<EtfHoldingBreakdownDto[]>([])
const allInstruments = ref<InstrumentDto[]>([])
const platformInstruments = ref<InstrumentDto[]>([])
const symbolToFundCurrency = computed(() => {
  const m = new Map<string, string>()
  for (const inst of allInstruments.value) {
    if (inst.fundCurrency) m.set(inst.symbol, inst.fundCurrency)
  }
  return m
})
const isLoading = ref(false)
const isError = ref(false)
const errorMessage = ref('')
const selectedEtfs = useLocalStorage<string[]>('portfolio_selected_etfs', [])
const searchQuery = useLocalStorage<string>('portfolio_etf_search', '')
const debouncedSearchQuery = refDebounced(searchQuery, 200)

const etfPlatformMetadata = computed(() => {
  if (masterHoldings.value.length === 0) return { etfs: [], platforms: [] }
  const etfSet = new Set<string>()
  const platformSet = new Set<string>()
  masterHoldings.value.forEach(holding => {
    holding.inEtfs.split(',').forEach(etf => {
      const trimmed = etf.trim()
      if (trimmed) etfSet.add(trimmed)
    })
    if (holding.platforms) {
      holding.platforms.split(',').forEach(p => {
        const trimmed = p.trim()
        if (trimmed) platformSet.add(trimmed)
      })
    }
  })
  return { etfs: Array.from(etfSet).sort(), platforms: Array.from(platformSet).sort() }
})

const availablePlatforms = computed(() => etfPlatformMetadata.value.platforms)

const { selectedPlatforms, isPlatformSelected, togglePlatform, toggleAllPlatforms } =
  usePlatformFilter('portfolio_etf_breakdown_platforms', availablePlatforms)

const availableEtfs = computed(() => etfPlatformMetadata.value.etfs)

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

const loadBreakdown = async (refreshMaster = false) => {
  isLoading.value = true
  isError.value = false
  errorMessage.value = ''

  try {
    const needsMaster = refreshMaster || masterHoldings.value.length === 0
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

watch(selectedEtfs, debouncedLoadBreakdown)

watch(selectedPlatforms, () => {
  debouncedLoadBreakdown()
  loadPlatformInstruments()
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

const clearSearch = () => {
  searchQuery.value = ''
}

const prefetchLogoCandidates = () => {
  const uuids = masterHoldings.value
    .map(h => h.holdingUuid)
    .filter((uuid): uuid is string => uuid !== null)
  if (uuids.length > 0) {
    logoService.prefetchCandidates(uuids)
  }
}

const loadAllInstruments = async () => {
  try {
    const response = await instrumentsService.getAll()
    allInstruments.value = response.instruments
  } catch {
    allInstruments.value = []
  }
}

const loadPlatformInstruments = async () => {
  try {
    const platformsParam = getPlatformsParam()
    const response = await instrumentsService.getAll(platformsParam)
    platformInstruments.value = response.instruments
  } catch {
    platformInstruments.value = []
  }
}

const currencySplit = computed(() => {
  const selected = new Set(selectedEtfs.value)
  const byCurrency = new Map<string, number>()
  for (const inst of platformInstruments.value) {
    if (!inst.fundCurrency) continue
    if (!selected.has(inst.symbol)) continue
    const value = inst.currentValue ?? 0
    if (value <= 0) continue
    byCurrency.set(inst.fundCurrency, (byCurrency.get(inst.fundCurrency) ?? 0) + value)
  }
  return Array.from(byCurrency.entries()).map(([currency, value]) => ({ currency, value }))
})

onMounted(async () => {
  await loadBreakdown()
  prefetchLogoCandidates()
  loadAllInstruments()
  loadPlatformInstruments()
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
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
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

.platform-btn.active {
  background: #0072b2;
  border-color: #0072b2;
}

.platform-btn.active:hover {
  background: #005a8c;
  border-color: #005a8c;
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
