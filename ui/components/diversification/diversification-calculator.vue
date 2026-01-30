<template>
  <div class="diversification-container">
    <div class="mb-4">
      <div class="d-flex justify-content-between align-items-center">
        <h2 class="mb-0">Diversification</h2>
        <div class="d-flex align-items-center gap-2">
          <span v-if="saveStatus === 'saving'" class="save-status text-muted">Saving...</span>
          <span v-else-if="saveStatus === 'saved'" class="save-status text-success">Saved</span>
          <span v-else-if="saveStatus === 'error'" class="save-status text-danger">
            Save failed
          </span>
          <div v-if="lastUpdatedText" class="last-updated">Updated {{ lastUpdatedText }}</div>
        </div>
      </div>
    </div>

    <div v-if="isLoadingEtfs" class="text-center py-4">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <template v-else>
      <AllocationTable
        :allocations="allocations"
        :input-mode="inputMode"
        :available-etfs="etfList"
        :is-loading-portfolio="isLoadingPortfolio"
        :total-investment="totalInvestment"
        :selected-platform="selectedPlatform"
        :available-platforms="availablePlatforms"
        :current-holdings-total="currentHoldingsTotal"
        :optimize-enabled="optimizeEnabled"
        :action-display-mode="actionDisplayMode"
        class="mb-4"
        @update:input-mode="onInputModeChange"
        @update:allocation="updateAllocation"
        @update:total-investment="onTotalInvestmentChange"
        @update:selected-platform="onPlatformChange"
        @update:optimize-enabled="onOptimizeChange"
        @update:action-display-mode="onActionDisplayModeChange"
        @add="addAllocation"
        @remove="removeAllocation"
        @clear="clearAllocations"
        @load-portfolio="loadFromPortfolio"
        @export="exportConfiguration"
        @import="importConfiguration"
      />

      <div v-if="result" class="results-section">
        <DiversificationStats
          :weighted-ter="result.weightedTer"
          :weighted-annual-return="result.weightedAnnualReturn"
          :total-unique-holdings="result.totalUniqueHoldings"
          :top10-percentage="result.concentration.top10Percentage"
        />

        <div class="row g-4">
          <div class="col-lg-4">
            <BreakdownCard title="Top Holdings" :items="holdingsBreakdown" />
          </div>
          <div class="col-lg-4">
            <BreakdownCard title="Sectors" :items="sectorsBreakdown" />
          </div>
          <div class="col-lg-4">
            <BreakdownCard title="Countries" :items="countriesBreakdown" />
          </div>
        </div>
      </div>

      <div v-if="isCalculating" class="text-center py-4">
        <div class="spinner-border text-primary" role="status">
          <span class="visually-hidden">Calculating...</span>
        </div>
      </div>

      <div v-if="error" class="alert alert-danger mt-3">
        {{ error }}
      </div>
    </template>

    <ConfigDialog
      v-model="showExportDialog"
      mode="export"
      :config="currentConfig"
      :valid-etf-ids="validEtfIds"
      modal-id="exportConfigDialog"
      @export="onExportComplete"
    />

    <ConfigDialog
      v-model="showImportDialog"
      mode="import"
      :config="currentConfig"
      :valid-etf-ids="validEtfIds"
      modal-id="importConfigDialog"
      @import="onImportComplete"
    />
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, watch, defineAsyncComponent, onMounted, onUnmounted } from 'vue'
import { useDebounceFn, useNow, useLocalStorage } from '@vueuse/core'
import { useQuery } from '@tanstack/vue-query'
import { diversificationService } from '../../services/diversification-service'
import { instrumentsService } from '../../services/instruments-service'
import { REFETCH_INTERVALS } from '../../constants'
import { formatRelativeTime } from '../../utils/formatters'
import AllocationTable from './allocation-table.vue'
import DiversificationStats from './diversification-stats.vue'
import BreakdownCard from './breakdown-card.vue'
import type {
  DiversificationCalculatorResponseDto,
  InstrumentDto,
} from '../../models/generated/domain-models'
import type { AllocationInput, CachedState, ActionDisplayMode } from './types'

const ConfigDialog = defineAsyncComponent(() => import('./config-dialog.vue'))

const {
  data: availableEtfs,
  isLoading: isLoadingEtfs,
  dataUpdatedAt,
} = useQuery({
  queryKey: ['diversification-etfs'],
  queryFn: diversificationService.getAvailableEtfs,
  refetchInterval: REFETCH_INTERVALS.DIVERSIFICATION_ETFS,
  staleTime: REFETCH_INTERVALS.DIVERSIFICATION_ETFS,
})

const now = useNow({ interval: 60000 })
const lastUpdatedText = computed(() => {
  if (!dataUpdatedAt.value) return ''
  return formatRelativeTime(dataUpdatedAt.value, now.value.getTime())
})

const allocations = ref<AllocationInput[]>([{ instrumentId: 0, value: 0 }])
const inputMode = ref<'percentage' | 'amount'>('percentage')
const totalInvestment = ref<number>(0)
const selectedPlatform = ref<string | null>(null)
const optimizeEnabled = ref(false)
const actionDisplayMode = useLocalStorage<ActionDisplayMode>(
  'portfolio_action_display_mode',
  'units'
)
const portfolioInstruments = ref<InstrumentDto[]>([])
const isCalculating = ref(false)
const isLoadingPortfolio = ref(false)
const error = ref('')
const result = ref<DiversificationCalculatorResponseDto | null>(null)
const isInitialized = ref(false)
const showExportDialog = ref(false)
const showImportDialog = ref(false)
const saveStatus = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const hasUnsavedChanges = ref(false)

const handleBeforeUnload = (e: BeforeUnloadEvent) => {
  if (hasUnsavedChanges.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  try {
    const response = await instrumentsService.getAll()
    portfolioInstruments.value = response.instruments
  } catch {
    portfolioInstruments.value = []
  }
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

const etfList = computed(() => availableEtfs.value ?? [])

const validEtfIds = computed(() => new Set(etfList.value.map(e => e.instrumentId)))

const availablePlatforms = computed(() => {
  const platforms = new Set<string>()
  portfolioInstruments.value.forEach(i => i.platforms?.forEach(p => platforms.add(p)))
  return Array.from(platforms).sort()
})

const currentHoldingsTotal = computed(() =>
  allocations.value.reduce((sum, a) => sum + (a.currentValue ?? 0), 0)
)

const currentConfig = computed(() => ({
  allocations: allocations.value,
  inputMode: inputMode.value,
  selectedPlatform: selectedPlatform.value,
  optimizeEnabled: optimizeEnabled.value,
  totalInvestment: totalInvestment.value,
  actionDisplayMode: actionDisplayMode.value,
}))

const toBreakdown = <T extends { percentage: number }>(
  items: T[] | undefined,
  getName: (item: T) => string
) =>
  items?.map(item => ({
    key: getName(item),
    name: getName(item),
    percentage: item.percentage,
  })) ?? []

const holdingsBreakdown = computed(() => toBreakdown(result.value?.holdings, h => h.name))
const sectorsBreakdown = computed(() => toBreakdown(result.value?.sectors, s => s.sector))
const countriesBreakdown = computed(() => toBreakdown(result.value?.countries, c => c.countryName))

const addAllocation = () => {
  allocations.value.push({ instrumentId: 0, value: 0 })
}

const removeAllocation = (index: number) => {
  if (allocations.value.length > 1) {
    allocations.value.splice(index, 1)
    onAllocationChange()
  }
}

const updateAllocation = (index: number, allocation: AllocationInput) => {
  allocations.value[index] = allocation
  onAllocationChange()
}

const getErrorMessage = (e: unknown): string => {
  if (e instanceof Error) {
    if (e.message.includes('Network Error') || e.message.includes('fetch')) {
      return 'Unable to connect to the server. Please check your internet connection and try again.'
    }
    if (e.message.includes('timeout')) {
      return 'The request timed out. Please try again.'
    }
    if (e.message.includes('500') || e.message.includes('Internal Server Error')) {
      return 'A server error occurred. Please try again later.'
    }
    return e.message
  }
  return 'An unexpected error occurred. Please try again.'
}

const calculateDiversification = async () => {
  const validAllocations = allocations.value.filter(a => a.instrumentId > 0 && a.value > 0)
  if (validAllocations.length < 1) {
    result.value = null
    return
  }
  isCalculating.value = true
  error.value = ''
  try {
    const requestAllocations = validAllocations.map(a => ({
      instrumentId: a.instrumentId,
      percentage: a.value,
    }))
    result.value = await diversificationService.calculate(requestAllocations)
  } catch (e) {
    error.value = getErrorMessage(e)
    result.value = null
  } finally {
    isCalculating.value = false
  }
}

const debouncedCalculate = useDebounceFn(calculateDiversification, 500)

const saveToDatabase = async () => {
  saveStatus.value = 'saving'
  try {
    await diversificationService.saveConfig({
      allocations: allocations.value,
      inputMode: inputMode.value,
      selectedPlatform: selectedPlatform.value,
      optimizeEnabled: optimizeEnabled.value,
      totalInvestment: totalInvestment.value,
      actionDisplayMode: actionDisplayMode.value,
    })
    saveStatus.value = 'saved'
    hasUnsavedChanges.value = false
    setTimeout(() => {
      if (saveStatus.value === 'saved') saveStatus.value = 'idle'
    }, 2000)
  } catch {
    saveStatus.value = 'error'
  }
}

const debouncedSave = useDebounceFn(saveToDatabase, 1000)

const onAllocationChange = () => {
  hasUnsavedChanges.value = true
  debouncedSave()
  debouncedCalculate()
}

const onInputModeChange = (mode: 'percentage' | 'amount') => {
  inputMode.value = mode
  hasUnsavedChanges.value = true
  debouncedSave()
  debouncedCalculate()
}

const loadFromPortfolio = async () => {
  isLoadingPortfolio.value = true
  error.value = ''
  try {
    const platforms = selectedPlatform.value ? [selectedPlatform.value] : undefined
    const response = await instrumentsService.getAll(platforms)
    const etfIds = new Set(etfList.value.map(e => e.instrumentId))
    const portfolioEtfs = response.instruments.filter(
      i => i.id !== null && etfIds.has(i.id) && (i.currentValue ?? 0) > 0
    )
    if (portfolioEtfs.length === 0) {
      error.value = selectedPlatform.value
        ? `No ETFs found on ${selectedPlatform.value}`
        : 'No ETFs found in your portfolio'
      return
    }
    const totalValue = portfolioEtfs.reduce((sum, i) => sum + (i.currentValue ?? 0), 0)
    allocations.value = portfolioEtfs
      .filter((i): i is typeof i & { id: number } => i.id !== null)
      .map(i => ({
        instrumentId: i.id,
        value:
          inputMode.value === 'percentage'
            ? Math.round(((i.currentValue ?? 0) / totalValue) * 1000) / 10
            : Math.round(i.currentValue ?? 0),
        currentValue: selectedPlatform.value ? (i.currentValue ?? 0) : undefined,
      }))
    hasUnsavedChanges.value = true
    debouncedSave()
    debouncedCalculate()
  } catch (e) {
    error.value = getErrorMessage(e)
  } finally {
    isLoadingPortfolio.value = false
  }
}

const clearAllocations = () => {
  allocations.value = [{ instrumentId: 0, value: 0 }]
  result.value = null
  hasUnsavedChanges.value = true
  debouncedSave()
}

const onPlatformChange = async (platform: string | null) => {
  selectedPlatform.value = platform
  hasUnsavedChanges.value = true
  debouncedSave()
  if (!platform) {
    allocations.value = allocations.value.map(a => ({ ...a, currentValue: undefined }))
    return
  }
  await loadCurrentValues(platform)
}

const onOptimizeChange = (enabled: boolean) => {
  optimizeEnabled.value = enabled
  hasUnsavedChanges.value = true
  debouncedSave()
}

const onActionDisplayModeChange = (mode: ActionDisplayMode) => {
  actionDisplayMode.value = mode
  hasUnsavedChanges.value = true
  debouncedSave()
}

const onTotalInvestmentChange = (value: number) => {
  totalInvestment.value = value
  hasUnsavedChanges.value = true
  debouncedSave()
}

const exportConfiguration = () => {
  showExportDialog.value = true
}

const importConfiguration = () => {
  showImportDialog.value = true
}

const onExportComplete = () => {
  showExportDialog.value = false
}

const onImportComplete = async (data: CachedState) => {
  allocations.value = data.allocations
  inputMode.value = data.inputMode
  selectedPlatform.value = data.selectedPlatform ?? null
  optimizeEnabled.value = data.optimizeEnabled ?? false
  totalInvestment.value = data.totalInvestment ?? 0
  actionDisplayMode.value = data.actionDisplayMode ?? 'units'
  hasUnsavedChanges.value = true
  if (selectedPlatform.value) {
    await loadCurrentValues(selectedPlatform.value)
  }
  debouncedSave()
  debouncedCalculate()
}

const loadCurrentValues = async (platform: string) => {
  try {
    const response = await instrumentsService.getAll([platform])
    const valueMap = new Map(
      response.instruments
        .filter((i): i is typeof i & { id: number } => i.id !== null)
        .map(i => [i.id, i.currentValue ?? 0])
    )
    allocations.value = allocations.value.map(a => ({
      ...a,
      currentValue: valueMap.get(a.instrumentId) ?? 0,
    }))
  } catch {
    allocations.value = allocations.value.map(a => ({ ...a, currentValue: 0 }))
  }
}

watch(
  availableEtfs,
  async newEtfs => {
    if (!newEtfs || newEtfs.length === 0 || isInitialized.value) return
    isInitialized.value = true
    const validIds = new Set(newEtfs.map(e => e.instrumentId))
    const dbConfig = await diversificationService.getConfig()
    if (!dbConfig) return
    const validAllocations = dbConfig.allocations.filter(
      a => a.instrumentId === 0 || validIds.has(a.instrumentId)
    )
    if (validAllocations.length === 0) return
    allocations.value = validAllocations.map(a => ({
      instrumentId: Number(a.instrumentId),
      value: Number(a.value),
    }))
    inputMode.value = dbConfig.inputMode
    selectedPlatform.value = dbConfig.selectedPlatform ?? null
    optimizeEnabled.value = dbConfig.optimizeEnabled ?? false
    totalInvestment.value = dbConfig.totalInvestment ?? 0
    actionDisplayMode.value = dbConfig.actionDisplayMode ?? 'units'
    if (selectedPlatform.value) {
      await loadCurrentValues(selectedPlatform.value)
    }
    debouncedCalculate()
  },
  { immediate: true }
)
</script>

<style scoped>
.diversification-container {
  max-width: min(1350px, 91vw);
  margin: 0 auto;
  padding: 1.5rem;
}

.last-updated {
  font-size: 0.75rem;
  color: #6b7280;
  background: #f9fafb;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  white-space: nowrap;
}

.save-status {
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .diversification-container {
    padding: 1rem;
  }
}
</style>
