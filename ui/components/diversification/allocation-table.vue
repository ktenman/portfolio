<template>
  <div class="allocation-section">
    <div class="header-section mb-3">
      <div class="header-row">
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
      <div v-if="inputMode === 'percentage'" class="investment-row">
        <div class="total-investment-input">
          <label class="d-none d-md-inline">Total to invest</label>
          <div class="input-group input-group-sm">
            <span class="input-group-text">€</span>
            <input
              :value="totalInvestment"
              type="number"
              class="form-control"
              min="0"
              step="1000"
              placeholder="10000"
              @input="onTotalInvestmentChange"
            />
          </div>
        </div>
        <div v-if="showInvestmentColumns" class="optimize-toggle">
          <input
            id="optimizeAllocation"
            v-model="optimizeEnabled"
            type="checkbox"
            class="form-check-input"
          />
          <label for="optimizeAllocation" class="form-check-label">Optimize</label>
        </div>
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
        :total-investment="totalInvestment"
        :disable-remove="allocations.length <= 1"
        :computed-units="
          getUnits(allocation.instrumentId, allocation.value, getEtfPrice(allocation.instrumentId))
        "
        :computed-unused="
          getUnused(allocation.instrumentId, allocation.value, getEtfPrice(allocation.instrumentId))
        "
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
            <th v-if="showInvestmentColumns" style="width: 70px">Units</th>
            <th v-if="showInvestmentColumns" style="width: 90px">Unused</th>
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
            <td v-if="showInvestmentColumns" class="text-muted small">
              {{
                formatUnits(
                  allocation.instrumentId,
                  allocation.value,
                  getEtfPrice(allocation.instrumentId)
                )
              }}
            </td>
            <td v-if="showInvestmentColumns" class="text-muted small">
              {{
                formatUnused(
                  allocation.instrumentId,
                  allocation.value,
                  getEtfPrice(allocation.instrumentId)
                )
              }}
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
      <div class="totals-section">
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
        <div v-if="showInvestmentColumns" class="total-row">
          <span class="total-label">Total Unused</span>
          <span class="total-value text-muted">€{{ totalUnused.toFixed(2) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { formatTer, formatReturn, formatCurrencyWithSymbol } from '../../utils/formatters'
import AllocationCard from './allocation-card.vue'
import type { EtfDetailDto } from '../../models/generated/domain-models'
import type { AllocationInput } from './types'

const props = defineProps<{
  allocations: AllocationInput[]
  inputMode: 'percentage' | 'amount'
  availableEtfs: EtfDetailDto[]
  isLoadingPortfolio: boolean
  totalInvestment: number
}>()

const emit = defineEmits<{
  'update:inputMode': ['percentage' | 'amount']
  'update:allocation': [index: number, allocation: AllocationInput]
  'update:totalInvestment': [value: number]
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

const showInvestmentColumns = computed(
  () => props.inputMode === 'percentage' && props.totalInvestment > 0
)

const optimizeEnabled = useLocalStorage('diversification-optimize', false)

const calculateBaseInvestment = (percentage: number, price: number | null) => {
  if (!percentage || !price || price <= 0 || props.totalInvestment <= 0) {
    return { allocated: 0, units: 0, unused: 0 }
  }
  const allocated = (props.totalInvestment * percentage) / 100
  const units = Math.floor(allocated / price)
  const unused = allocated - units * price
  return { allocated, units, unused }
}

const optimizedAllocation = computed(() => {
  if (!showInvestmentColumns.value || !optimizeEnabled.value) return new Map<number, number>()
  const validAllocations = props.allocations.filter(a => a.instrumentId > 0 && a.value > 0)
  if (validAllocations.length === 0) return new Map<number, number>()
  const fundData = validAllocations.map(allocation => {
    const price = getEtfPrice(allocation.instrumentId) ?? 0
    const allocated = (props.totalInvestment * allocation.value) / 100
    const exactUnits = price > 0 ? allocated / price : 0
    const baseUnits = Math.floor(exactUnits)
    const remainder = exactUnits - baseUnits
    return {
      id: allocation.instrumentId,
      price,
      baseUnits,
      remainder,
      currentUnits: baseUnits,
    }
  })
  let totalSpent = fundData.reduce((sum, f) => sum + f.currentUnits * f.price, 0)
  let remaining = props.totalInvestment - totalSpent
  const sortedByRemainder = [...fundData]
    .filter(f => f.price > 0)
    .sort((a, b) => b.remainder - a.remainder)
  for (const fund of sortedByRemainder) {
    if (fund.price <= remaining) {
      fund.currentUnits++
      remaining -= fund.price
      totalSpent += fund.price
    }
  }
  let improved = true
  while (improved && remaining > 0) {
    improved = false
    let bestFund: (typeof fundData)[0] | null = null
    let bestDeficit = -Infinity
    for (const fund of fundData) {
      if (fund.price <= 0 || fund.price > remaining) continue
      const currentPercent =
        totalSpent > 0 ? ((fund.currentUnits * fund.price) / totalSpent) * 100 : 0
      const totalPercent = validAllocations.reduce((sum, a) => sum + a.value, 0)
      const targetPercent =
        ((validAllocations.find(a => a.instrumentId === fund.id)?.value ?? 0) / totalPercent) * 100
      const deficit = targetPercent - currentPercent
      if (deficit > bestDeficit) {
        bestDeficit = deficit
        bestFund = fund
      }
    }
    if (bestFund) {
      bestFund.currentUnits++
      remaining -= bestFund.price
      totalSpent += bestFund.price
      improved = true
    }
  }
  const result = new Map<number, number>()
  fundData.forEach(f => result.set(f.id, f.currentUnits))
  return result
})

const getUnits = (instrumentId: number, percentage: number, price: number | null): number => {
  if (optimizeEnabled.value && optimizedAllocation.value.has(instrumentId)) {
    return optimizedAllocation.value.get(instrumentId) ?? 0
  }
  return calculateBaseInvestment(percentage, price).units
}

const getUnused = (instrumentId: number, percentage: number, price: number | null): number => {
  const units = getUnits(instrumentId, percentage, price)
  if (!price || units === 0) return 0
  const allocated = (props.totalInvestment * percentage) / 100
  return allocated - units * price
}

const formatUnits = (instrumentId: number, percentage: number, price: number | null): string => {
  const units = getUnits(instrumentId, percentage, price)
  return units > 0 ? units.toString() : '-'
}

const formatUnused = (instrumentId: number, percentage: number, price: number | null): string => {
  const units = getUnits(instrumentId, percentage, price)
  if (units === 0) return '-'
  const unused = getUnused(instrumentId, percentage, price)
  return `€${unused.toFixed(2)}`
}

const totalUnused = computed(() => {
  if (!showInvestmentColumns.value) return 0
  return props.allocations.reduce((sum, allocation) => {
    const price = getEtfPrice(allocation.instrumentId)
    const unused = getUnused(allocation.instrumentId, allocation.value, price)
    return sum + unused
  }, 0)
})

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

const onTotalInvestmentChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  emit('update:totalInvestment', Number(target.value) || 0)
}
</script>

<style scoped>
.allocation-section {
  background: var(--bs-white);
  border: 1px solid var(--bs-gray-300);
  border-radius: 0.5rem;
  padding: 1.5rem;
}

.header-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.investment-row {
  display: flex;
  align-items: center;
  gap: 1.5rem;
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

.totals-section {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
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

.total-investment-input {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.total-investment-input label {
  font-size: 0.75rem;
  color: var(--bs-gray-600);
  white-space: nowrap;
}

.total-investment-input .input-group {
  width: 140px;
}

.total-investment-input .input-group-text {
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
}

.total-investment-input input {
  font-size: 0.875rem;
}

.optimize-toggle {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.optimize-toggle .form-check-input {
  margin: 0;
  cursor: pointer;
}

.optimize-toggle .form-check-label {
  font-size: 0.75rem;
  color: var(--bs-gray-600);
  cursor: pointer;
  white-space: nowrap;
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

  .header-row {
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .investment-row {
    justify-content: space-between;
  }

  .total-investment-input .input-group {
    min-width: 140px;
    flex-wrap: nowrap;
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
