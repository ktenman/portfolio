<template>
  <div class="allocation-section">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h5 class="mb-0">ETF Allocation</h5>
      <div class="mode-buttons">
        <button
          type="button"
          class="mode-btn"
          :class="{ active: inputMode === 'percentage' }"
          @click="$emit('update:inputMode', 'percentage')"
        >
          <span class="d-none d-sm-inline">Percentage</span>
          <span class="d-sm-none">%</span>
        </button>
        <button
          type="button"
          class="mode-btn"
          :class="{ active: inputMode === 'amount' }"
          @click="$emit('update:inputMode', 'amount')"
        >
          <span class="d-none d-sm-inline">Amount (EUR)</span>
          <span class="d-sm-none">EUR</span>
        </button>
      </div>
    </div>

    <!-- Mobile Card View -->
    <div class="mobile-cards-wrapper d-block d-md-none">
      <AllocationCard
        v-for="(allocation, index) in allocations"
        :key="index"
        :allocation="allocation"
        :available-etfs="availableEtfsForRow(index)"
        :input-mode="inputMode"
        :disable-remove="allocations.length <= 1"
        @update:allocation="updateAllocationAtIndex(index, $event)"
        @remove="$emit('remove', index)"
      />
    </div>

    <!-- Desktop Table View -->
    <div class="d-none d-md-block table-responsive">
      <table class="table table-sm allocation-table">
        <thead>
          <tr>
            <th>ETF</th>
            <th>Name</th>
            <th>Price</th>
            <th>TER</th>
            <th>Annual Return</th>
            <th style="width: 150px">
              {{ inputMode === 'percentage' ? 'Allocation %' : 'Amount EUR' }}
            </th>
            <th style="width: 50px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(allocation, index) in allocations" :key="index">
            <td>
              <select
                :value="allocation.instrumentId"
                class="form-select form-select-sm"
                @change="onInstrumentChange(index, $event)"
              >
                <option :value="0" disabled>Select ETF</option>
                <option
                  v-for="etf in availableEtfsForRow(index)"
                  :key="etf.instrumentId"
                  :value="etf.instrumentId"
                >
                  {{ etf.symbol }}
                </option>
              </select>
            </td>
            <td class="text-muted small">{{ getEtfName(allocation.instrumentId) }}</td>
            <td class="text-muted small">
              {{ formatEtfPrice(getEtfPrice(allocation.instrumentId)) }}
            </td>
            <td class="text-muted small">{{ formatTer(getEtfTer(allocation.instrumentId)) }}</td>
            <td class="text-muted small">
              {{ formatReturn(getEtfReturn(allocation.instrumentId)) }}
            </td>
            <td>
              <input
                :value="allocation.value"
                type="number"
                class="form-control form-control-sm"
                min="0"
                :max="inputMode === 'percentage' ? 100 : undefined"
                :step="inputMode === 'percentage' ? 1 : 100"
                @input="onValueChange(index, $event)"
              />
            </td>
            <td>
              <button
                type="button"
                class="remove-btn"
                aria-label="Remove allocation"
                :disabled="allocations.length <= 1"
                @click="$emit('remove', index)"
              >
                &times;
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="allocation-footer">
      <div class="action-buttons">
        <button
          type="button"
          class="action-btn"
          title="Add ETF"
          aria-label="Add ETF"
          @click="$emit('add')"
        >
          <span class="d-none d-sm-inline">+ Add ETF</span>
          <span class="d-sm-none">+</span>
        </button>
        <button
          type="button"
          class="action-btn"
          title="Load from Portfolio"
          aria-label="Load from Portfolio"
          :disabled="isLoadingPortfolio"
          @click="$emit('loadPortfolio')"
        >
          <span
            v-if="isLoadingPortfolio"
            class="spinner-border spinner-border-sm"
            role="status"
          ></span>
          <template v-else>
            <span class="d-sm-none">↓</span>
            <span class="d-none d-sm-inline">Load from Portfolio</span>
          </template>
        </button>
        <button
          type="button"
          class="action-btn"
          title="Export"
          aria-label="Export"
          @click="$emit('export')"
        >
          <span class="d-none d-sm-inline">Export</span>
          <span class="d-sm-none">↗</span>
        </button>
        <button
          type="button"
          class="action-btn"
          title="Import"
          aria-label="Import"
          @click="$emit('import')"
        >
          <span class="d-none d-sm-inline">Import</span>
          <span class="d-sm-none">↙</span>
        </button>
        <button
          type="button"
          class="action-btn danger"
          title="Clear"
          aria-label="Clear"
          :disabled="allocations.length === 1 && allocations[0].instrumentId === 0"
          @click="$emit('clear')"
        >
          <span class="d-none d-sm-inline">Clear</span>
          <span class="d-sm-none">✕</span>
        </button>
      </div>
      <div class="total-row">
        <span class="total-label">Total</span>
        <span class="total-value" :class="{ invalid: !isValidTotal, valid: isValidTotal }">
          {{
            inputMode === 'percentage'
              ? `${totalAllocation.toFixed(1)}%`
              : formatCurrencyWithSymbol(totalAllocation)
          }}
          <span v-if="inputMode === 'percentage' && !isValidTotal" class="total-hint">
            (should be 100%)
          </span>
        </span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { formatTer, formatReturn, formatCurrencyWithSymbol } from '../../utils/formatters'
import AllocationCard from './allocation-card.vue'
import type { EtfDetailDto } from '../../models/generated/domain-models'
import type { AllocationInput } from './types'

const props = defineProps<{
  allocations: AllocationInput[]
  inputMode: 'percentage' | 'amount'
  availableEtfs: EtfDetailDto[]
  isLoadingPortfolio: boolean
}>()

const emit = defineEmits<{
  'update:inputMode': ['percentage' | 'amount']
  'update:allocation': [index: number, allocation: AllocationInput]
  add: []
  remove: [index: number]
  clear: []
  loadPortfolio: []
  export: []
  import: []
}>()

const formatEtfPrice = (value: number | null) => (value === null ? '-' : `€${value.toFixed(2)}`)

const totalAllocation = computed(() =>
  props.allocations.reduce((sum, a) => sum + (a.value || 0), 0)
)

const isValidTotal = computed(() => {
  if (props.inputMode === 'percentage') {
    return Math.abs(totalAllocation.value - 100) < 0.1
  }
  return totalAllocation.value > 0
})

const availableEtfsForRow = (rowIndex: number) => {
  const selectedIds = props.allocations
    .filter((_, i) => i !== rowIndex)
    .map(a => a.instrumentId)
    .filter(id => id > 0)
  return props.availableEtfs.filter(etf => !selectedIds.includes(etf.instrumentId))
}

const getEtfName = (instrumentId: number): string =>
  props.availableEtfs.find(e => e.instrumentId === instrumentId)?.name || ''

const getEtfTer = (instrumentId: number): number | null =>
  props.availableEtfs.find(e => e.instrumentId === instrumentId)?.ter ?? null

const getEtfReturn = (instrumentId: number): number | null =>
  props.availableEtfs.find(e => e.instrumentId === instrumentId)?.annualReturn ?? null

const getEtfPrice = (instrumentId: number): number | null =>
  props.availableEtfs.find(e => e.instrumentId === instrumentId)?.currentPrice ?? null

const onInstrumentChange = (index: number, event: Event) => {
  const target = event.target as HTMLSelectElement
  emit('update:allocation', index, {
    ...props.allocations[index],
    instrumentId: Number(target.value),
  })
}

const onValueChange = (index: number, event: Event) => {
  const target = event.target as HTMLInputElement
  emit('update:allocation', index, {
    ...props.allocations[index],
    value: Number(target.value) || 0,
  })
}

const updateAllocationAtIndex = (index: number, allocation: AllocationInput) => {
  emit('update:allocation', index, allocation)
}
</script>

<style scoped>
.allocation-section {
  background: var(--bs-white);
  border: 1px solid var(--bs-gray-300);
  border-radius: 0.5rem;
  padding: 1.5rem;
}

.allocation-table {
  margin-bottom: 1rem;
}

.allocation-table th {
  font-weight: 500;
  color: var(--bs-gray-600);
  font-size: 0.875rem;
  border-bottom: 2px solid var(--bs-gray-300);
}

.allocation-table td {
  vertical-align: middle;
}

.allocation-table tbody tr:nth-child(odd) {
  background-color: var(--bs-gray-100);
}

.allocation-table tbody tr:nth-child(even) {
  background-color: var(--bs-white);
}

.allocation-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--bs-gray-300);
}

.total-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.total-label {
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--bs-gray-600);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.total-value {
  font-size: 1rem;
  font-weight: 600;
  color: var(--bs-gray-900);
}

.total-value.valid {
  color: var(--bs-success);
}

.total-value.invalid {
  color: var(--bs-danger);
}

.total-hint {
  font-size: 0.75rem;
  font-weight: 400;
  margin-left: 0.25rem;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.action-btn {
  padding: 0.3125rem 0.625rem;
  border: 1px solid var(--bs-gray-300);
  background: var(--bs-white);
  color: var(--bs-gray-600);
  border-radius: 0.375rem;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.12s ease;
  white-space: nowrap;
}

.action-btn:hover:not(:disabled) {
  background: var(--bs-gray-100);
  border-color: var(--bs-gray-400);
  color: var(--bs-gray-700);
}

.action-btn:active:not(:disabled) {
  background: var(--bs-gray-200);
  transform: scale(0.98);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-btn.danger:hover:not(:disabled) {
  background: #fef2f2;
  border-color: #fecaca;
  color: var(--bs-danger);
}

.mode-buttons {
  display: flex;
  gap: 0;
}

.mode-btn {
  padding: 0.3125rem 0.625rem;
  border: 1px solid var(--bs-gray-300);
  background: var(--bs-white);
  color: var(--bs-gray-600);
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.12s ease;
  white-space: nowrap;
}

.mode-btn:first-child {
  border-radius: 0.375rem 0 0 0.375rem;
  border-right: none;
}

.mode-btn:last-child {
  border-radius: 0 0.375rem 0.375rem 0;
}

.mode-btn:hover:not(.active) {
  background: var(--bs-gray-100);
  border-color: var(--bs-gray-400);
  color: var(--bs-gray-700);
}

.mode-btn.active {
  background: var(--bs-gray-700);
  color: var(--bs-white);
  border-color: var(--bs-gray-700);
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
  color: var(--bs-danger);
}

.remove-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.mobile-cards-wrapper {
  margin-bottom: 1rem;
}

@media (max-width: 767.98px) {
  .allocation-section {
    padding: 1rem;
  }

  .allocation-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .action-buttons {
    justify-content: center;
  }

  .total-row {
    justify-content: center;
  }
}
</style>
