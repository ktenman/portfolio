<template>
  <crud-layout
    title="Instruments"
    :show-add-button="false"
    @add="openAddModal"
    @title-click="handleTitleClick"
  >
    <template #title-suffix>
      <filter-toggle
        v-if="availablePlatforms.length > 0"
        v-model="filtersOpen"
        :selected="selectedPlatforms.length"
        :available="availablePlatforms.length"
      />
    </template>

    <template #toolbar>
      <div class="controls-row">
        <div class="period-selector-container">
          <label class="period-label hidden md:inline" for="periodSelect">Period:</label>
          <select
            id="periodSelect"
            v-model="selectedPeriod"
            class="form-select form-select-sm period-select"
            aria-label="Price change period"
          >
            <option v-for="range in TIME_RANGES" :key="range" :value="range">
              {{ range }}
            </option>
          </select>
        </div>
        <div class="toggle-container">
          <span class="toggle-label">Active only</span>
          <label class="toggle-switch">
            <input
              v-model="showActiveOnly"
              type="checkbox"
              role="switch"
              aria-label="Show active instruments only"
            />
            <span class="toggle-slider"></span>
          </label>
        </div>
      </div>
    </template>

    <template #subtitle>
      <platform-filter
        v-if="filtersOpen && availablePlatforms.length > 0"
        class="mt-3"
        :available="availablePlatforms"
        :selected="selectedPlatforms"
        @toggle="togglePlatform"
        @toggle-all="toggleAllPlatforms"
      />
    </template>

    <template #content>
      <instrument-table
        :instruments="items || []"
        :portfolio-xirr="portfolioXirr"
        :is-loading="isLoading"
        :is-error="isError"
        :error-message="error?.message"
        :selected-period="selectedPeriod"
        :sort-state="sortState"
        :on-sort="toggleSort"
        @show-xirr-windows="isXirrWindowsModalOpen = true"
        @show-annual-windows="isAnnualWindowsModalOpen = true"
      />
    </template>

    <template #modals>
      <instrument-modal
        v-model:open="isInstrumentModalOpen"
        :instrument="selectedItem || {}"
        @save="onSave"
      />
      <xirr-windows-modal
        v-model:open="isXirrWindowsModalOpen"
        :platforms="effectivePlatformsForXirr"
      />
      <annual-windows-modal
        v-model:open="isAnnualWindowsModalOpen"
        :platforms="effectivePlatformsForXirr"
      />
    </template>
  </crud-layout>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { useToast } from '../../composables/use-toast'
import { TIME_RANGES, usePriceChangePeriod } from '../../composables/use-time-range'
import { useSortableTable } from '../../composables/use-sortable-table'
import { useAuthState } from '../../composables/use-auth-state'
import { usePlatformFilter } from '../../composables/use-platform-filter'
import CrudLayout from '../shared/crud-layout.vue'
import PlatformFilter from '../shared/platform-filter.vue'
import FilterToggle from '../shared/filter-toggle.vue'
import InstrumentTable from './instrument-table.vue'
import InstrumentModal from './instrument-modal.vue'
import XirrWindowsModal from './xirr-windows-modal.vue'
import AnnualWindowsModal from './annual-windows-modal.vue'
import { instrumentsService } from '../../services/api'
import { InstrumentDto } from '../../models/generated/domain-models'
import { STORAGE_KEYS, REFETCH_INTERVALS } from '../../constants'

const selectedItem = ref<InstrumentDto | null>(null)
const showActiveOnly = useLocalStorage<boolean>(STORAGE_KEYS.SHOW_ACTIVE_ONLY, true)
const filtersOpen = useLocalStorage<boolean>(STORAGE_KEYS.INSTRUMENTS_FILTERS_OPEN, true)
const isInstrumentModalOpen = ref(false)
const isXirrWindowsModalOpen = ref(false)
const isAnnualWindowsModalOpen = ref(false)
const selectedPeriod = usePriceChangePeriod()
const queryClient = useQueryClient()
const toast = useToast()
const { isAuthenticated } = useAuthState()

const { data: allInstruments } = useQuery({
  queryKey: ['instruments-all'],
  queryFn: () => instrumentsService.getAll(),
  enabled: isAuthenticated,
})

const availablePlatforms = computed(() => {
  if (!allInstruments.value) return []

  const platformSet = new Set<string>()
  allInstruments.value.instruments.forEach(instrument => {
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

const { selectedPlatforms, togglePlatform, toggleAllPlatforms } = usePlatformFilter(
  STORAGE_KEYS.SELECTED_PLATFORMS,
  availablePlatforms
)

const {
  data: rawItems,
  isLoading,
  isError,
  error,
} = useQuery({
  queryKey: computed(() => ['instruments', selectedPlatforms.value, selectedPeriod.value]),
  queryFn: () => {
    if (
      selectedPlatforms.value.length === 0 ||
      selectedPlatforms.value.length === availablePlatforms.value.length
    ) {
      return instrumentsService.getAll(undefined, selectedPeriod.value)
    }
    return instrumentsService.getAll(selectedPlatforms.value, selectedPeriod.value)
  },
  refetchInterval: REFETCH_INTERVALS.INSTRUMENTS,
  enabled: isAuthenticated,
})

const filteredItems = computed(() => {
  if (!rawItems.value) return []
  return rawItems.value.instruments.filter(instrument => {
    const hasValue = (instrument.currentValue || 0) > 0
    const hasProfit = (instrument.profit || 0) !== 0
    if (showActiveOnly.value) return hasValue
    return hasValue || hasProfit
  })
})

const {
  sortedItems: items,
  sortState,
  toggleSort,
} = useSortableTable(filteredItems, 'currentValue', 'desc')

const portfolioXirr = computed(() => rawItems.value?.portfolioXirr ?? null)

const effectivePlatformsForXirr = computed<string[]>(() => {
  const selected = selectedPlatforms.value
  if (selected.length === 0 || selected.length === availablePlatforms.value.length) return []
  return selected
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
    isInstrumentModalOpen.value = false
    selectedItem.value = null
  },
  onError: (error: Error) => {
    toast.error(`Failed to save instrument: ${error.message}`)
  },
})

const openAddModal = () => {
  selectedItem.value = null
  isInstrumentModalOpen.value = true
}

const onSave = (instrument: Partial<InstrumentDto>) => {
  saveMutation.mutate(instrument)
}

const handleTitleClick = async () => {
  try {
    await instrumentsService.refreshPrices()
    toast.success('Price refresh triggered! Data will update shortly.')
    setTimeout(() => {
      queryClient.invalidateQueries({ queryKey: ['instruments'] })
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['summaries'] })
    }, 2000)
  } catch {
    toast.error('Failed to trigger price refresh')
  }
}
</script>

<style scoped>
.controls-row {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.toggle-container {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.toggle-label {
  font-size: var(--text-label);
  font-weight: 500;
  color: var(--color-ink-muted);
  white-space: nowrap;
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 2.5rem;
  height: 1.5rem;
  margin: 0;
  cursor: pointer;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  inset: 0;
  background-color: var(--color-hairline);
  border-radius: 1.5rem;
  transition: all 0.2s ease;
}

.toggle-slider::before {
  position: absolute;
  content: '';
  height: 1.125rem;
  width: 1.125rem;
  left: 0.1875rem;
  bottom: 0.1875rem;
  background-color: white;
  border-radius: 50%;
  transition: all 0.2s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.toggle-switch input:checked + .toggle-slider {
  background-color: var(--color-brass);
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(1rem);
}

.toggle-switch input:focus + .toggle-slider {
  box-shadow: 0 0 0 2px var(--color-brass-wash);
}

.period-selector-container {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.period-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--color-ink-muted);
  margin: 0;
}

.period-select {
  width: auto;
  min-width: 5.5rem;
  cursor: pointer;
}

@media (max-width: 768px) {
  .period-selector-container {
    width: auto;
  }

  .period-select {
    flex: 1;
  }
}

@media (max-width: 992px) and (orientation: landscape) {
  .controls-row {
    display: none !important;
  }
}

@media (max-height: 500px) {
  .controls-row {
    display: none !important;
  }
}
</style>
