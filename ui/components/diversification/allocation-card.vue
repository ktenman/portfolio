<template>
  <div class="allocation-card">
    <div class="allocation-card-header">
      <select
        :value="allocation.instrumentId"
        class="form-select form-select-sm flex-grow-1"
        @change="onInstrumentChange"
      >
        <option :value="0" disabled>Select ETF</option>
        <option v-for="etf in availableEtfs" :key="etf.instrumentId" :value="etf.instrumentId">
          {{ etf.symbol }}
        </option>
      </select>
      <button
        type="button"
        class="remove-btn"
        :disabled="disableRemove"
        aria-label="Remove allocation"
        @click="$emit('remove')"
      >
        &times;
      </button>
    </div>
    <div v-if="etfName" class="allocation-card-name">
      {{ etfName }}
    </div>
    <div v-if="allocation.instrumentId > 0" class="allocation-card-metrics">
      <div class="metric-group">
        <span class="metric-value">{{ formattedPrice }}</span>
        <span class="metric-label">Price</span>
      </div>
      <div class="metric-group">
        <span class="metric-value">{{ formattedTer }}</span>
        <span class="metric-label">TER</span>
      </div>
      <div class="metric-group">
        <span class="metric-value">{{ formattedReturn }}</span>
        <span class="metric-label">Annual</span>
      </div>
    </div>
    <div class="allocation-card-input">
      <label>{{ inputLabel }}</label>
      <input
        :value="allocation.value"
        type="number"
        class="form-control form-control-sm"
        min="0"
        :max="inputMode === 'percentage' ? 100 : undefined"
        :step="inputMode === 'percentage' ? 1 : 100"
        @input="onValueChange"
      />
    </div>
    <div
      v-if="showInvestmentInfo && allocation.instrumentId > 0"
      class="allocation-card-investment"
    >
      <div class="investment-item">
        <label>Units</label>
        <span class="investment-value">{{ formattedUnits }}</span>
      </div>
      <div class="investment-item">
        <label>Unused</label>
        <span class="investment-value">{{ formattedUnused }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { formatTer, formatReturn } from '../../utils/formatters'
import type { EtfDetailDto } from '../../models/generated/domain-models'
import type { AllocationInput } from './types'

const props = defineProps<{
  allocation: AllocationInput
  availableEtfs: EtfDetailDto[]
  inputMode: 'percentage' | 'amount'
  totalInvestment: number
  disableRemove: boolean
  computedUnits?: number
  computedUnused?: number
}>()

const emit = defineEmits<{
  'update:allocation': [allocation: AllocationInput]
  remove: []
}>()

const selectedEtf = computed(() =>
  props.availableEtfs.find(e => e.instrumentId === props.allocation.instrumentId)
)

const etfName = computed(() => selectedEtf.value?.name || '')

const formattedPrice = computed(() => {
  const price = selectedEtf.value?.currentPrice
  return price === null || price === undefined ? '-' : `€${price.toFixed(2)}`
})

const formattedTer = computed(() => formatTer(selectedEtf.value?.ter ?? null))

const formattedReturn = computed(() => formatReturn(selectedEtf.value?.annualReturn ?? null))

const showInvestmentInfo = computed(
  () => props.inputMode === 'percentage' && props.totalInvestment > 0
)

const localInvestmentCalc = computed(() => {
  const price = selectedEtf.value?.currentPrice
  const percentage = props.allocation.value
  if (!percentage || !price || price <= 0 || props.totalInvestment <= 0) {
    return { units: 0, unused: 0 }
  }
  const allocated = (props.totalInvestment * percentage) / 100
  const units = Math.floor(allocated / price)
  const unused = allocated - units * price
  return { units, unused }
})

const formattedUnits = computed(() => {
  const units = props.computedUnits ?? localInvestmentCalc.value.units
  return units > 0 ? units.toString() : '-'
})

const formattedUnused = computed(() => {
  const units = props.computedUnits ?? localInvestmentCalc.value.units
  const unused = props.computedUnused ?? localInvestmentCalc.value.unused
  if (units === 0) return '-'
  return `€${unused.toFixed(2)}`
})

const inputLabel = computed(() =>
  props.inputMode === 'percentage' ? 'Allocation %' : 'Amount EUR'
)

const onInstrumentChange = (event: Event) => {
  const target = event.target as HTMLSelectElement
  emit('update:allocation', {
    ...props.allocation,
    instrumentId: Number(target.value),
  })
}

const onValueChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  emit('update:allocation', {
    ...props.allocation,
    value: Number(target.value) || 0,
  })
}
</script>

<style scoped>
.allocation-card {
  background: var(--bs-gray-100);
  border: 1px solid var(--bs-gray-300);
  border-radius: 0.5rem;
  padding: 0.75rem;
  margin-bottom: 0.5rem;
}

.allocation-card:last-child {
  margin-bottom: 0;
}

.allocation-card-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.allocation-card-header .form-select {
  flex: 1;
}

.allocation-card-name {
  font-size: 0.8125rem;
  color: var(--bs-gray-600);
  margin-bottom: 0.5rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.allocation-card-metrics {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid var(--bs-gray-300);
  border-bottom: 1px solid var(--bs-gray-300);
  padding: 0.5rem 0;
  margin-bottom: 0.5rem;
}

.allocation-card-metrics .metric-group {
  text-align: center;
  flex: 1;
  min-width: 0;
}

.allocation-card-metrics .metric-value {
  display: block;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--bs-gray-800);
  margin-bottom: 0.125rem;
}

.allocation-card-metrics .metric-label {
  display: block;
  font-size: 0.6875rem;
  color: var(--bs-gray-600);
  text-transform: uppercase;
  letter-spacing: 0.025em;
}

.allocation-card-input {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.allocation-card-input label {
  font-size: 0.75rem;
  color: var(--bs-gray-600);
  white-space: nowrap;
}

.allocation-card-input input {
  flex: 1;
  max-width: 120px;
}

.allocation-card-investment {
  display: flex;
  gap: 1rem;
  margin-top: 0.5rem;
  padding-top: 0.5rem;
  border-top: 1px dashed var(--bs-gray-300);
}

.allocation-card-investment .investment-item {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.allocation-card-investment label {
  font-size: 0.75rem;
  color: var(--bs-gray-600);
  white-space: nowrap;
}

.allocation-card-investment .investment-value {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--bs-gray-700);
}

.remove-btn {
  width: 1.5rem;
  height: 1.5rem;
  padding: 0;
  border: 1px solid var(--bs-gray-300);
  background: var(--bs-white);
  color: var(--bs-gray-500);
  border-radius: 0.25rem;
  font-size: 1rem;
  line-height: 1;
  cursor: pointer;
  transition: all 0.12s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.remove-btn:hover:not(:disabled) {
  background: #fef2f2;
  border-color: #fecaca;
  color: #dc2626;
}

.remove-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
</style>
