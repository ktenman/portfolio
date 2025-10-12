<template>
  <crud-layout
    add-button-id="addNewInstrument"
    add-button-text="New InstrumentDto"
    title="Instruments"
    :show-add-button="false"
    @add="openAddModal"
    @title-click="handleTitleClick"
  >
    <template #subtitle>
      <div class="subtitle-container mt-2">
        <div v-if="availablePlatforms.length > 0" class="platform-filter-container">
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
        <div class="scenario-tester-container">
          <div class="scenario-header">
            <span class="scenario-title">Scenario Tester</span>
          </div>

          <div class="scenario-presets">
            <button
              v-for="scenario in presetScenarios"
              :key="scenario.id"
              class="scenario-btn"
              :disabled="isApplying"
              @click="applyScenario(scenario.percentage)"
            >
              <span class="scenario-name">{{ scenario.name }}</span>
              <span class="scenario-value">
                {{ scenario.percentage > 0 ? '+' : '' }}{{ scenario.percentage }}%
              </span>
            </button>
            <button class="scenario-btn custom" @click="showCustom = !showCustom">Custom</button>
          </div>

          <transition name="expand">
            <div v-if="showCustom" class="custom-scenario">
              <div class="input-row">
                <input
                  v-model.number="customPercentage"
                  type="number"
                  step="5"
                  min="-90"
                  max="300"
                  placeholder="Enter %"
                  class="percentage-input"
                />
                <button
                  class="btn-apply-scenario"
                  :disabled="!customPercentage || isApplying"
                  @click="applyCustomScenario"
                >
                  {{ isApplying ? 'Applying...' : 'Apply' }}
                </button>
              </div>
              <div v-if="customPercentage && totalValue" class="impact-text">
                Impact:
                <span :class="impactClass">
                  {{ difference >= 0 ? '+' : '' }}{{ formatCurrency(Math.abs(difference)) }}
                </span>
              </div>
            </div>
          </transition>
        </div>
      </div>
    </template>

    <template #content>
      <instrument-table
        :instruments="items || []"
        :is-loading="isLoading"
        :is-error="isError"
        :error-message="error?.message"
        @edit="openEditModal"
      />
    </template>

    <template #modals>
      <instrument-modal :instrument="selectedItem || {}" @save="onSave" />
    </template>
  </crud-layout>
</template>

<script setup lang="ts">
import { computed, watch, ref } from 'vue'
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { useToast } from '../../composables/use-toast'
import { usePriceUpdates } from '../../composables/use-price-updates'
import { useLocalStorage } from '@vueuse/core'
import { useBootstrapModal } from '../../composables/use-bootstrap-modal'
import CrudLayout from '../shared/crud-layout.vue'
import InstrumentTable from './instrument-table.vue'
import InstrumentModal from './instrument-modal.vue'
import { instrumentsService } from '../../services/instruments-service'
import { InstrumentDto } from '../../models/generated/domain-models'

const selectedItem = ref<InstrumentDto | null>(null)
const selectedPlatforms = useLocalStorage<string[]>('portfolio_selected_platforms', [])
const showCustom = ref(false)
const customPercentage = ref<number | null>(null)
const isApplying = ref(false)
const { show: showModal, hide: hideModal } = useBootstrapModal('instrumentModal')
const queryClient = useQueryClient()
const toast = useToast()

usePriceUpdates()

const presetScenarios = [
  { id: 1, name: 'Crash', percentage: -50, emoji: '📉', class: 'crash' },
  { id: 2, name: 'Decline', percentage: -30, emoji: '📉', class: 'decline' },
  { id: 3, name: 'Dip', percentage: -10, emoji: '📊', class: 'dip' },
  { id: 4, name: 'Growth', percentage: 20, emoji: '📈', class: 'growth' },
  { id: 5, name: 'Bull', percentage: 50, emoji: '📈', class: 'bull' },
]

const { data: allInstruments } = useQuery({
  queryKey: ['instruments-all'],
  queryFn: () => instrumentsService.getAll(),
})

const availablePlatforms = computed(() => {
  if (!allInstruments.value) return []

  const platformSet = new Set<string>()
  allInstruments.value.forEach(instrument => {
    if (
      instrument.platforms &&
      instrument.platforms.length > 0 &&
      ((instrument.totalInvestment && Number(instrument.totalInvestment) > 0) ||
        (instrument.quantity && Number(instrument.quantity) > 0))
    ) {
      instrument.platforms.forEach(platform => {
        platformSet.add(platform)
      })
    }
  })

  return Array.from(platformSet).sort()
})

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

const {
  data: items,
  isLoading,
  isError,
  error,
} = useQuery({
  queryKey: computed(() => ['instruments', selectedPlatforms.value]),
  queryFn: () => {
    if (
      selectedPlatforms.value.length === 0 ||
      selectedPlatforms.value.length === availablePlatforms.value.length
    ) {
      return instrumentsService.getAll()
    }
    return instrumentsService.getAll(selectedPlatforms.value)
  },
  refetchInterval: 30000,
})

const saveMutation = useMutation({
  mutationFn: (data: Partial<InstrumentDto>) => {
    if (selectedItem.value?.id) {
      return instrumentsService.update(selectedItem.value.id, data)
    }
    return instrumentsService.create(data)
  },
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['instruments'] })
    queryClient.invalidateQueries({ queryKey: ['summaries'] })
    queryClient.invalidateQueries({ queryKey: ['transactions'] })
    toast.success(`InstrumentDto ${selectedItem.value?.id ? 'updated' : 'created'} successfully`)
    hideModal()
    selectedItem.value = null
  },
  onError: (error: Error) => {
    toast.error(`Failed to save instrument: ${error.message}`)
  },
})

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

const formatPlatformName = (platform: string): string => {
  const platformMap: Record<string, string> = {
    TRADING212: 'Trading 212',
    LIGHTYEAR: 'Lightyear',
    SWEDBANK: 'Swedbank',
    BINANCE: 'Binance',
    COINBASE: 'Coinbase',
    LHV: 'LHV',
    AVIVA: 'Aviva',
    UNKNOWN: 'Unknown',
  }

  return platformMap[platform] || platform
}

const formatCurrency = (value: number): string => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(value)
}

const totalValue = computed(() => {
  if (!items.value) return 0
  return items.value.reduce((sum, instrument) => {
    return sum + (instrument.currentValue || 0)
  }, 0)
})

const projectedTotal = computed(() => {
  if (!customPercentage.value || !totalValue.value) return 0
  return totalValue.value * (1 + customPercentage.value / 100)
})

const difference = computed(() => projectedTotal.value - totalValue.value)

const impactClass = computed(() => ({
  positive: customPercentage.value && customPercentage.value > 0,
  negative: customPercentage.value && customPercentage.value < 0,
}))

const openAddModal = () => {
  selectedItem.value = null
  showModal()
}

const openEditModal = (instrument: InstrumentDto) => {
  selectedItem.value = { ...instrument }
  showModal()
}

const onSave = (instrument: Partial<InstrumentDto>) => {
  saveMutation.mutate(instrument)
}

const applyScenario = async (percentage: number) => {
  const multiplier = 1 + percentage / 100
  isApplying.value = true
  try {
    await instrumentsService.applyPriceCoefficient(multiplier)
  } catch (error) {
    toast.error(`Failed to apply scenario: ${(error as Error).message}`)
  } finally {
    isApplying.value = false
  }
}

const applyCustomScenario = async () => {
  if (customPercentage.value) {
    await applyScenario(customPercentage.value)
    customPercentage.value = null
    showCustom.value = false
  }
}

const handleTitleClick = async () => {
  try {
    await instrumentsService.refreshPrices()
  } catch {
    toast.error('Failed to trigger price refresh')
  }
}
</script>

<style scoped>
.subtitle-container {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.platform-filter-container {
  display: flex;
  align-items: center;
  padding: 0;
  background: transparent;
}

.scenario-tester-container {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.scenario-header {
  display: flex;
  align-items: center;
}

.scenario-title {
  margin: 0;
  font-size: 0.75rem;
  font-weight: 500;
  color: #6b7280;
}

.scenario-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.scenario-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.3125rem 0.625rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  background: white;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.12s ease;
  font-size: 0.75rem;
  font-weight: 500;
  white-space: nowrap;
}

.scenario-btn:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #4b5563;
}

.scenario-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.scenario-name {
  font-weight: 500;
}

.scenario-value {
  font-size: 0.7rem;
  color: #94a3b8;
  font-weight: 400;
}

.custom-scenario {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  margin-top: 0.5rem;
}

.input-row {
  display: flex;
  gap: 0.375rem;
  align-items: center;
}

.percentage-input {
  flex: 1;
  padding: 0.3125rem 0.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  background: white;
  font-size: 0.75rem;
  font-weight: 500;
  color: #374151;
  transition: border-color 0.2s;
}

.percentage-input:focus {
  outline: none;
  border-color: #cbd5e1;
}

.percentage-input::placeholder {
  color: #94a3b8;
}

.btn-apply-scenario {
  padding: 0.3125rem 0.625rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  background: white;
  color: #6b7280;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.12s ease;
  white-space: nowrap;
}

.btn-apply-scenario:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #4b5563;
}

.btn-apply-scenario:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.impact-text {
  font-size: 0.7rem;
  color: #6b7280;
  padding-left: 0.25rem;
}

.impact-text .positive {
  color: #059669;
  font-weight: 500;
}

.impact-text .negative {
  color: #dc2626;
  font-weight: 500;
}

.expand-enter-active,
.expand-leave-active {
  transition: all 0.3s ease;
  max-height: 500px;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  max-height: 0;
  opacity: 0;
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
  background: #4b5563;
  color: white;
  border-color: #4b5563;
  font-weight: 500;
}

.platform-btn.active:hover {
  background: #374151;
  border-color: #374151;
  color: white;
}

@media (max-width: 768px) {
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
  .platform-filter-container {
    align-items: center;
  }
}
</style>
