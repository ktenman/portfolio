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
      <div v-if="availablePlatforms.length > 0" class="platform-filter-container mt-2">
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
        <div class="period-selector-container">
          <label class="period-label d-none d-md-inline">Period:</label>
          <select v-model="selectedPeriod" class="period-select">
            <option v-for="p in periods" :key="p.value" :value="p.value">
              {{ p.label }}
            </option>
          </select>
        </div>
      </div>
    </template>

    <template #content>
      <instrument-table
        :instruments="items || []"
        :is-loading="isLoading"
        :is-error="isError"
        :error-message="error?.message"
        :selected-period="selectedPeriod"
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
import { useLocalStorage } from '@vueuse/core'
import { useBootstrapModal } from '../../composables/use-bootstrap-modal'
import { usePriceChangePeriod } from '../../composables/use-price-change-period'
import CrudLayout from '../shared/crud-layout.vue'
import InstrumentTable from './instrument-table.vue'
import InstrumentModal from './instrument-modal.vue'
import { instrumentsService } from '../../services/instruments-service'
import { InstrumentDto } from '../../models/generated/domain-models'
import { formatPlatformName } from '../../utils/platform-utils'

const selectedItem = ref<InstrumentDto | null>(null)
const selectedPlatforms = useLocalStorage<string[]>('portfolio_selected_platforms', [])
const { show: showModal, hide: hideModal } = useBootstrapModal('instrumentModal')
const { selectedPeriod, periods } = usePriceChangePeriod()
const queryClient = useQueryClient()
const toast = useToast()

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
  refetchInterval: 2000,
})

const items = computed(() => {
  if (!rawItems.value) return []

  const sorted = [...rawItems.value].sort((a, b) => {
    const valueA = a.currentValue || 0
    const valueB = b.currentValue || 0
    return valueB - valueA
  })

  return sorted
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
.platform-filter-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0;
  background: transparent;
  gap: 1rem;
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

.period-selector-container {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.period-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #6b7280;
  margin: 0;
}

.period-select {
  padding: 0.3125rem 0.625rem;
  border: 1px solid #e2e8f0;
  background: white;
  color: #4b5563;
  border-radius: 0.375rem;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.12s ease;
  min-width: 4rem;
}

.period-select:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.period-select:focus {
  outline: none;
  border-color: #4b5563;
  box-shadow: 0 0 0 3px rgba(75, 85, 99, 0.1);
}

@media (max-width: 768px) {
  .platform-filter-container {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
  }

  .platform-buttons {
    width: 100%;
  }

  .platform-separator {
    display: none;
  }

  .period-selector-container {
    width: 100%;
  }

  .period-select {
    flex: 1;
  }
}

@media (min-width: 769px) {
  .platform-filter-container {
    align-items: center;
  }
}

@media (max-width: 992px) and (orientation: landscape) {
  .period-selector-container {
    display: none !important;
  }
}

@media (max-height: 500px) {
  .period-selector-container {
    display: none !important;
  }
}
</style>
