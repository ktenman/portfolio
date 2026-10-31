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
      <div class="controls-row">
        <nav class="flex gap-7 max-md:gap-4" aria-label="Instrument views">
          <router-link
            v-for="tab in VIEWS"
            :key="tab.to"
            :to="tab.to"
            exact-active-class="active"
            class="relative px-0.5 py-2.5 text-base leading-6 font-medium text-ink-soft transition-colors after:absolute after:inset-x-0 after:-bottom-px after:h-0.5 after:transition-colors hover:text-ink hover:after:bg-hairline-strong [&.active]:font-semibold [&.active]:text-ink [&.active]:after:bg-brass"
          >
            {{ tab.label }}
          </router-link>
        </nav>
        <div class="controls-group">
          <div v-if="view === 'table'" class="toggle-container">
            <span class="toggle-label">Active only</span>
            <label
              class="toggle-switch md-down:before:absolute md-down:before:-inset-x-0.5 md-down:before:top-1/2 md-down:before:h-11 md-down:before:-translate-y-1/2"
            >
              <input
                v-model="showActiveOnly"
                type="checkbox"
                role="switch"
                aria-label="Show active instruments only"
              />
              <span class="toggle-slider"></span>
            </label>
          </div>
          <instrument-period-picker :selected="selectedPeriod" @select="selectedPeriod = $event" />
        </div>
      </div>
      <instrument-heatmap
        v-if="view === 'heatmap'"
        :instruments="openPositions"
        :portfolio-xirr="portfolioXirr"
        :range-change="rangeChange"
        :period="selectedPeriod"
        :is-loading="isLoading"
        :is-error="isError"
        :error-message="error?.message"
      />
      <instrument-table
        v-else
        :instruments="items || []"
        :portfolio-xirr="portfolioXirr"
        :is-loading="isLoading"
        :is-error="isError"
        :error-message="error?.message"
        :selected-period="selectedPeriod"
        :range-change="rangeChange"
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
      <xirr-windows-modal v-model:open="isXirrWindowsModalOpen" :platforms="activePlatforms" />
      <annual-windows-modal v-model:open="isAnnualWindowsModalOpen" :platforms="activePlatforms" />
    </template>
  </crud-layout>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { useRoute } from 'vue-router'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/vue-query'
import { useToast } from '../../composables/use-toast'
import { usePriceChangePeriod } from '../../composables/use-time-range'
import { useSortableTable } from '../../composables/use-sortable-table'
import { useAuthState } from '../../composables/use-auth-state'
import { usePlatformFilter } from '../../composables/use-platform-filter'
import CrudLayout from '../shared/crud-layout.vue'
import PlatformFilter from '../shared/platform-filter.vue'
import FilterToggle from '../shared/filter-toggle.vue'
import InstrumentHeatmap from './instrument-heatmap.vue'
import InstrumentPeriodPicker from './instrument-period-picker.vue'
import InstrumentTable from './instrument-table.vue'
import InstrumentModal from './instrument-modal.vue'
import XirrWindowsModal from './xirr-windows-modal.vue'
import AnnualWindowsModal from './annual-windows-modal.vue'
import { instrumentsService, portfolioSummaryService } from '../../services/api'
import { InstrumentDto } from '../../models/generated/domain-models'
import { STORAGE_KEYS } from '../../constants'

type InstrumentsView = 'table' | 'heatmap'

const VIEWS = [
  { to: '/instruments', label: 'Table' },
  { to: '/instruments/heatmap', label: 'Heatmap' },
]

const selectedItem = ref<InstrumentDto | null>(null)
const route = useRoute()
const view = computed<InstrumentsView>(() =>
  route.params.view === 'heatmap' ? 'heatmap' : 'table'
)
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

const { selectedPlatforms, activePlatforms, togglePlatform, toggleAllPlatforms } =
  usePlatformFilter(STORAGE_KEYS.SELECTED_PLATFORMS, availablePlatforms)

const {
  data: rawItems,
  isLoading,
  isError,
  error,
} = useQuery({
  queryKey: computed(() => ['instruments', activePlatforms.value, selectedPeriod.value]),
  queryFn: () => instrumentsService.getAll(activePlatforms.value, selectedPeriod.value),
  enabled: isAuthenticated,
})

const { data: rangeChange } = useQuery({
  queryKey: computed(() => [
    'instruments',
    'range-change',
    activePlatforms.value,
    selectedPeriod.value,
  ]),
  queryFn: () =>
    portfolioSummaryService.getRangeChange(selectedPeriod.value, activePlatforms.value),
  placeholderData: keepPreviousData,
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

const openPositions = computed(
  () => rawItems.value?.instruments.filter(instrument => (instrument.currentValue || 0) > 0) ?? []
)

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
  } catch {
    toast.error('Failed to trigger price refresh')
  }
}
</script>

<style scoped>
.controls-row {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-bottom: 1rem;
  padding-bottom: 1px;
}

.controls-row::before {
  content: '';
  position: absolute;
  inset-inline: 0;
  top: 2.75rem;
  border-bottom: 1px solid var(--color-hairline-strong);
}

.controls-row > nav {
  margin-right: auto;
}

.controls-group {
  display: flex;
  align-items: center;
  gap: 0.75rem;
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
