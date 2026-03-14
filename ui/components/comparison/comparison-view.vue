<template>
  <div class="comparison-container">
    <h2 class="mb-3">Compare Instruments</h2>

    <div class="mb-3">
      <div class="d-flex flex-wrap gap-2 mb-2">
        <span v-for="id in selectedIds" :key="id" class="instrument-tag" :style="tagStyle(id)">
          {{ instrumentNameMap.get(id) ?? id }}
          <button type="button" class="tag-close" @click="removeInstrument(id)">&times;</button>
        </span>
      </div>
      <div class="search-dropdown" ref="dropdownRef">
        <input
          v-model="searchQuery"
          type="text"
          class="form-control form-control-sm"
          placeholder="Search instruments..."
          @focus="showDropdown = true"
        />
        <ul v-if="showDropdown && filteredInstruments.length > 0" class="dropdown-list">
          <li
            v-for="inst in filteredInstruments"
            :key="inst.id!"
            class="dropdown-item"
            @click="selectInstrument(inst)"
          >
            <span class="fw-medium">{{ inst.name }}</span>
            <span class="text-muted ms-1">{{ shortSymbol(inst.symbol) }}</span>
          </li>
        </ul>
      </div>
    </div>

    <div class="period-buttons mb-3">
      <button
        v-for="p in periods"
        :key="p"
        type="button"
        class="period-btn"
        :class="[{ active: period === p }, { capped: cappedPeriods.has(p) && period !== p }]"
        @click="period = p"
      >
        {{ p }}
      </button>
    </div>

    <div v-if="isLoading" class="text-center py-4">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <div v-else-if="data && data.instruments.length > 0">
      <div class="text-muted small mb-2">
        {{ data.startDate }} &mdash; {{ data.endDate }}
        <span v-if="isTruncated" class="text-warning ms-2">
          (limited history &mdash; {{ period }} data not available)
        </span>
      </div>
      <ComparisonChart :chart-data="chartData" :chart-options="chartOptions" />

      <div class="legend mt-3">
        <div
          v-for="(inst, i) in data.instruments"
          :key="inst.instrumentId"
          class="legend-item d-flex align-items-center gap-2 mb-1"
        >
          <span class="legend-dot" :style="{ backgroundColor: colors[i % colors.length] }"></span>
          <span class="fw-medium">{{ shortSymbol(inst.symbol) }}</span>
          <span class="text-muted">{{ inst.name }}</span>
          <span v-if="inst.currentPrice" class="text-muted">{{ inst.currentPrice }}</span>
          <span :class="inst.totalChangePercent >= 0 ? 'text-success' : 'text-danger'">
            {{ inst.totalChangePercent >= 0 ? '+' : '' }}{{ inst.totalChangePercent.toFixed(2) }}%
          </span>
        </div>
      </div>
    </div>

    <div v-else-if="selectedIds.length < 2" class="text-muted text-center py-4">
      Select at least 2 instruments to compare
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onClickOutside, useLocalStorage } from '@vueuse/core'
import { useQuery } from '@tanstack/vue-query'
import { instrumentsService } from '../../services/instruments-service'
import { useComparisonChart } from '../../composables/use-comparison-chart'
import { useInstrumentColors } from '../../composables/use-instrument-colors'
import { useTruncationDetection } from '../../composables/use-period-resolution'
import { shortSymbol } from '../../utils/instrument-formatters'
import ComparisonChart from './comparison-chart.vue'
import type { InstrumentDto } from '../../models/generated/domain-models'

const periods = ['1M', '6M', 'YTD', '1Y', '2Y', '3Y', '4Y', '5Y', 'MAX'] as const
const period = useLocalStorage('comparison_period', '1Y')
const selectedIds = useLocalStorage<number[]>('comparison_instrument_ids', [])
const searchQuery = ref('')
const showDropdown = ref(false)
const dropdownRef = ref<HTMLElement | null>(null)

const { data: instrumentsResponse } = useQuery({
  queryKey: ['instruments'],
  queryFn: () => instrumentsService.getAll(),
  staleTime: 60000,
})

const allInstruments = computed<InstrumentDto[]>(() => instrumentsResponse.value?.instruments ?? [])

const instrumentNameMap = computed(() => new Map(allInstruments.value.map(i => [i.id, i.name])))

const EXCLUDED_CATEGORIES = new Set(['CASH', 'CRYPTO'])

const filteredInstruments = computed(() => {
  const query = searchQuery.value.toLowerCase()
  const selected = new Set(selectedIds.value)
  return allInstruments.value
    .filter(i => i.id !== null && !selected.has(i.id!) && !EXCLUDED_CATEGORIES.has(i.category))
    .filter(
      i => !query || i.name.toLowerCase().includes(query) || i.symbol.toLowerCase().includes(query)
    )
    .sort((a, b) => a.name.localeCompare(b.name))
})

const selectInstrument = (inst: InstrumentDto) => {
  if (inst.id === null || selectedIds.value.includes(inst.id)) return
  selectedIds.value = [...selectedIds.value, inst.id]
  searchQuery.value = ''
  showDropdown.value = false
}

const removeInstrument = (id: number) => {
  selectedIds.value = selectedIds.value.filter(i => i !== id)
}

onClickOutside(dropdownRef, () => {
  showDropdown.value = false
})

const queryEnabled = computed(() => selectedIds.value.length >= 2)

const { data, isLoading } = useQuery({
  queryKey: computed(() => ['instrument-comparison', selectedIds.value, period.value]),
  queryFn: () => instrumentsService.compare(selectedIds.value, period.value),
  enabled: queryEnabled,
  staleTime: 30000,
})

const { chartData, chartOptions, colors } = useComparisonChart(data)

const instrumentColorMap = computed(
  () =>
    new Map(
      data.value?.instruments.map((inst, i) => [inst.instrumentId, colors[i % colors.length]]) ?? []
    )
)

const { tagStyle } = useInstrumentColors(selectedIds, instrumentColorMap, colors)
const { isTruncated, cappedPeriods } = useTruncationDetection(data, period, periods)
</script>

<style scoped>
.comparison-container {
  max-width: min(1200px, 95vw);
  margin: 0 auto;
  padding: 1.5rem;
}

.instrument-tag {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.3125rem 0.625rem;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  font-weight: 500;
  white-space: nowrap;
}

.tag-close {
  background: none;
  border: none;
  color: inherit;
  font-size: 1.125rem;
  line-height: 1;
  cursor: pointer;
  padding: 0;
  opacity: 0.7;
}

.tag-close:hover {
  opacity: 1;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-item {
  font-size: 0.875rem;
}

.search-dropdown {
  position: relative;
}

.dropdown-list {
  position: absolute;
  z-index: 1050;
  width: 100%;
  max-height: 250px;
  overflow-y: auto;
  background: white;
  border: 1px solid #dee2e6;
  border-top: none;
  border-radius: 0 0 0.25rem 0.25rem;
  list-style: none;
  padding: 0;
  margin: 0;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

.dropdown-item {
  padding: 0.375rem 0.75rem;
  cursor: pointer;
  font-size: 0.875rem;
}

.dropdown-item:hover {
  background-color: #f8f9fa;
}

.period-buttons {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.375rem;
}

.period-btn {
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

.period-btn:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #4b5563;
}

.period-btn:active {
  background: #f1f5f9;
  transform: scale(0.98);
}

.period-btn.active {
  background: #4b5563;
  color: white;
  border-color: #4b5563;
}

.period-btn.active:hover {
  background: #374151;
  border-color: #374151;
  color: white;
}

.period-btn.capped {
  opacity: 0.45;
  border-style: dashed;
}

@media (max-width: 768px) {
  .comparison-container {
    padding: 1rem;
  }
}
</style>
