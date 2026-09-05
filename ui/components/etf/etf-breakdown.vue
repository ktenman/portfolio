<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3">
    <div class="mb-6">
      <div class="flex flex-col gap-1">
        <div class="flex items-center gap-3">
          <h2 class="mb-0">ETF Breakdown</h2>
          <filter-toggle
            v-if="availableEtfs.length > 0"
            v-model="filtersOpen"
            :selected="selectedEtfs.length"
            :available="availableEtfs.length"
            :active="isFiltered"
          />
        </div>
        <etf-breakdown-header
          v-if="!isLoading"
          :selected-etfs="selectedEtfs"
          :available-etfs="availableEtfs"
        />
      </div>
      <div v-if="filtersOpen && availableEtfs.length > 0" class="etf-filter-container mt-3">
        <div class="etf-buttons">
          <button
            v-for="etf in availableEtfs"
            :key="etf"
            class="etf-btn"
            :class="{ active: isEtfSelected(etf) }"
            :title="symbolToName.get(etf) ?? etf"
            @click="toggleEtf(etf)"
            type="button"
          >
            <currency-flag :currency="symbolToFundCurrency.get(etf)" :size="14" />
            {{ getSymbolOnly(etf) }}
          </button>
          <span class="etf-separator"></span>
          <button class="etf-btn etf-btn-ghost" @click="toggleAllEtfs" type="button">
            {{ selectedEtfs.length === availableEtfs.length ? 'Clear All' : 'Select All' }}
          </button>
        </div>
      </div>
      <platform-filter
        v-if="filtersOpen && availablePlatforms.length > 1"
        class="mt-2"
        :available="availablePlatforms"
        :selected="selectedPlatforms"
        @toggle="togglePlatform"
        @toggle-all="toggleAllPlatforms"
      />
    </div>

    <div v-if="!isLoading && holdings.length > 0" class="charts-section mb-6">
      <etf-breakdown-chart :chart-data="activeChartData" :benchmark-label="benchmarkLabel">
        <template #actions>
          <div class="breakdown-tabs" role="group" aria-label="Breakdown dimension">
            <button
              v-for="tab in breakdownTabs"
              :key="tab.key"
              class="breakdown-tab"
              :class="{ active: activeTab === tab.key }"
              :aria-pressed="activeTab === tab.key"
              type="button"
              @click="activeTab = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>
        </template>
      </etf-breakdown-chart>
      <etf-breakdown-stats
        :total-value="totalValue"
        :unique-holdings="holdings.length"
        :weighted-ter="weightedMetrics.ter"
        :weighted-annual-return="weightedMetrics.annualReturn"
        :currency-split="currencySplit"
      />
    </div>

    <div class="search-container mb-4">
      <div class="search-input-wrapper">
        <svg class="search-icon" viewBox="0 0 16 16" aria-hidden="true">
          <circle cx="7" cy="7" r="4.75" fill="none" stroke="currentColor" stroke-width="1.5" />
          <path
            d="M10.6 10.6 14 14"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
          />
        </svg>
        <input
          v-model="searchQuery"
          type="text"
          class="search-input"
          placeholder="Search by name, ticker, sector, industry, or country..."
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
import { etfBreakdownService, instrumentsService, logoService } from '../../services/api'
import {
  buildSectorChartData,
  buildIndustryChartData,
  buildCompanyChartData,
  buildCountryChartData,
  calculateWeightedMetrics,
  getFilterParam,
  type ChartDataItem,
} from '../../services/etf-chart-service'
import type { EtfHoldingBreakdownDto, InstrumentDto } from '../../models/generated/domain-models'
import EtfBreakdownHeader from './etf-breakdown-header.vue'
import EtfBreakdownStats from './etf-breakdown-stats.vue'
import EtfBreakdownChart from './etf-breakdown-chart.vue'
import EtfBreakdownTable from './etf-breakdown-table.vue'
import CurrencyFlag from '../shared/currency-flag.vue'
import PlatformFilter from '../shared/platform-filter.vue'
import FilterToggle from '../shared/filter-toggle.vue'
import { STORAGE_KEYS } from '../../constants'

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
const symbolToName = computed(() => {
  const m = new Map<string, string>()
  for (const inst of allInstruments.value) {
    m.set(inst.symbol, inst.name)
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

const { selectedPlatforms, activePlatforms, togglePlatform, toggleAllPlatforms } =
  usePlatformFilter('portfolio_etf_breakdown_platforms', availablePlatforms)

const availableEtfs = computed(() => etfPlatformMetadata.value.etfs)

const filtersOpen = useLocalStorage<boolean>(STORAGE_KEYS.ETF_FILTERS_OPEN, false)

const isFiltered = computed(
  () =>
    selectedEtfs.value.length !== availableEtfs.value.length ||
    selectedPlatforms.value.length !== availablePlatforms.value.length
)

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
      h.holdingIndustry?.toLowerCase().includes(query) ||
      h.holdingCountryName?.toLowerCase().includes(query)
  )
})

const totalValue = computed(() => holdings.value.reduce((sum, h) => sum + h.totalValueEur, 0))

const sectorChartData = computed<ChartDataItem[]>(() => buildSectorChartData(holdings.value))

const companyChartData = computed<ChartDataItem[]>(() => buildCompanyChartData(holdings.value))

const countryChartData = computed<ChartDataItem[]>(() => buildCountryChartData(holdings.value))

const breakdownTabs = [
  { key: 'sectors', label: 'Sectors' },
  { key: 'industries', label: 'Industries' },
  { key: 'companies', label: 'Top holdings' },
  { key: 'countries', label: 'Countries' },
] as const

type BreakdownTab = (typeof breakdownTabs)[number]['key']

const activeTab = ref<BreakdownTab>('sectors')

const benchmarkHoldings = ref<EtfHoldingBreakdownDto[]>([])

const industryChartData = computed<ChartDataItem[]>(() =>
  buildIndustryChartData(holdings.value, benchmarkHoldings.value)
)

const activeChartData = computed(() => {
  if (activeTab.value === 'industries') return industryChartData.value
  if (activeTab.value === 'companies') return companyChartData.value
  if (activeTab.value === 'countries') return countryChartData.value
  return sectorChartData.value
})

const getEtfsParam = (): string[] | undefined =>
  getFilterParam(selectedEtfs.value, availableEtfs.value)

const loadBreakdown = async (refreshMaster = false) => {
  isLoading.value = true
  isError.value = false
  errorMessage.value = ''

  try {
    const needsMaster = refreshMaster || masterHoldings.value.length === 0
    const platformsParam = activePlatforms.value
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

const BENCHMARK_CHAIN = ['WEBN:GER:EUR', 'VWCE:GER:EUR']

const benchmarkSymbol = computed(() =>
  BENCHMARK_CHAIN.find(symbol => availableEtfs.value.includes(symbol))
)

const benchmarkLabel = computed(() => {
  const symbol = benchmarkSymbol.value
  if (!symbol || benchmarkHoldings.value.length === 0) return undefined
  return getSymbolOnly(symbol)
})

let benchmarkRequest: Promise<void> | null = null

const loadBenchmark = () => {
  const symbol = benchmarkSymbol.value
  if (!symbol || benchmarkRequest) return
  benchmarkRequest = etfBreakdownService
    .getBreakdown([symbol], undefined)
    .then(rows => {
      benchmarkHoldings.value = rows
    })
    .catch(() => {
      benchmarkHoldings.value = []
    })
}

watch([activeTab, benchmarkSymbol], () => {
  if (activeTab.value === 'industries') loadBenchmark()
})

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
    const platformsParam = activePlatforms.value
    const response = await instrumentsService.getAll(platformsParam)
    platformInstruments.value = response.instruments
  } catch {
    platformInstruments.value = []
  }
}

const weightedMetrics = computed(() =>
  calculateWeightedMetrics(platformInstruments.value, selectedEtfs.value)
)

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
.etf-filter-container {
  display: flex;
  align-items: center;
  padding: 0;
  background: transparent;
}

.charts-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 1rem;
}

@media (min-width: 1024px) {
  .charts-section {
    grid-template-columns: minmax(0, 1fr) 17rem;
    align-items: start;
  }
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
  background-color: var(--color-hairline-strong);
  display: inline-block;
}

.breakdown-tabs {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.25rem;
}

.breakdown-tab {
  padding: 0.3125rem 0.75rem;
  border: 1px solid transparent;
  border-radius: var(--radius-container);
  background: transparent;
  font-size: 0.8125rem;
  color: var(--color-ink-soft);
  cursor: pointer;
  white-space: nowrap;
}

.breakdown-tab:hover {
  background: var(--color-surface-hover);
  color: var(--color-ink);
}

.breakdown-tab.active {
  border-color: var(--color-brass);
  background: var(--color-brass-wash);
  color: var(--color-brass-deep);
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

.search-icon {
  position: absolute;
  left: 0.6875rem;
  top: 50%;
  transform: translateY(-50%);
  width: 15px;
  height: 15px;
  color: var(--color-ink-faint);
  pointer-events: none;
  transition: color var(--transition-fast);
}

.search-input-wrapper:focus-within .search-icon {
  color: var(--color-brass);
}

.search-input {
  width: 100%;
  padding: 0.4375rem 2rem 0.4375rem 2.125rem;
  border: 1px solid var(--color-control-border);
  border-radius: var(--radius-container);
  font-size: var(--text-sm);
  color: var(--color-ink);
  background: var(--color-surface-subtle);
  transition: background-color var(--transition-fast);
}

.search-input:focus {
  background: var(--color-surface);
}

.search-input::placeholder {
  color: var(--color-ink-soft);
}

.search-clear-btn {
  position: absolute;
  right: 0.4375rem;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.25rem;
  height: 1.25rem;
  padding: 0;
  background: none;
  border: none;
  border-radius: var(--radius-control);
  color: var(--color-ink-soft);
  font-size: 1.125rem;
  line-height: 1;
  cursor: pointer;
}

.search-clear-btn:hover {
  background: var(--color-brass-wash);
  color: var(--color-ink);
}

.search-results-count {
  font-size: var(--text-label);
  color: var(--color-ink-muted);
  white-space: nowrap;
}

@media (max-width: 768px) {
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

  .breakdown-tab {
    padding: 0.3125rem 0.5rem;
  }
}

@media (min-width: 769px) {
  .etf-filter-container {
    align-items: center;
  }
}
</style>
