<template>
  <div class="allocation-section">
    <div class="header-section mb-3">
      <div class="header-row">
        <h5 class="mb-0">ETF Allocation</h5>
      </div>
      <div class="investment-row">
        <div v-if="availablePlatforms.length > 0" class="platform-selector">
          <label class="d-none d-md-inline">Platform</label>
          <select
            class="form-select form-select-sm"
            :value="selectedPlatform ?? ''"
            @change="onPlatformChange"
          >
            <option value="">All platforms</option>
            <option v-for="p in availablePlatforms" :key="p" :value="p">
              {{ formatPlatformName(p) }}
            </option>
          </select>
        </div>
        <div v-if="showRebalanceColumns" class="current-holdings">
          <label class="d-none d-md-inline">Current</label>
          <span class="holdings-value">€{{ currentHoldingsTotal.toFixed(2) }}</span>
        </div>
        <div class="total-investment-input">
          <label class="d-none d-md-inline">
            {{ showRebalanceColumns ? 'New investment' : 'Total to invest' }}
          </label>
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
            :checked="optimizeEnabled"
            type="checkbox"
            class="form-check-input"
            @change="emit('update:optimizeEnabled', ($event.target as HTMLInputElement).checked)"
          />
          <label for="optimizeAllocation" class="form-check-label">Optimize</label>
        </div>
        <div v-if="showInvestmentColumns || showRebalanceActionColumn" class="display-mode-toggle">
          <button
            type="button"
            class="display-mode-btn"
            :class="{ active: actionDisplayMode === 'units' }"
            @click="emit('update:actionDisplayMode', 'units')"
          >
            Units
          </button>
          <button
            type="button"
            class="display-mode-btn"
            :class="{ active: actionDisplayMode === 'amount' }"
            @click="emit('update:actionDisplayMode', 'amount')"
          >
            Amount
          </button>
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
        :total-investment="totalInvestment"
        :disable-remove="allocations.length <= 1"
        :show-rebalance-mode="!!showRebalanceColumns"
        :action-display-mode="actionDisplayMode"
        :is-buy="showRebalanceColumns ? getRebalanceData(allocation).isBuy : true"
        :computed-units="
          showRebalanceColumns
            ? getRebalanceData(allocation).units
            : getUnits(
                allocation.instrumentId,
                allocation.value,
                getEtfPrice(allocation.instrumentId)
              )
        "
        :computed-amount="
          showRebalanceColumns
            ? actionDisplayMode === 'amount'
              ? getRebalanceFractionalAmount(allocation)
              : getRebalanceAmount(allocation)
            : calculateInvestmentAmount(totalInvestment, allocation.value)
        "
        :computed-unused="
          showRebalanceColumns
            ? undefined
            : getUnused(
                allocation.instrumentId,
                allocation.value,
                getEtfPrice(allocation.instrumentId)
              )
        "
        :after-percent="showRebalanceColumns ? getAfterPercent(allocation) : undefined"
        @update:allocation="updateAllocationAtIndex(index, $event)"
        @remove="$emit('remove', index)"
      />
    </div>

    <!-- Desktop Table View -->
    <div class="d-none d-md-block table-responsive">
      <table class="table table-sm allocation-table">
        <thead>
          <tr>
            <th class="sortable" @click="toggleSort('symbol')">
              <span class="th-content">
                ETF
                <span class="sort-indicator" :class="getSortIndicatorClass('symbol')">
                  <i class="sort-arrow-up">▲</i>
                  <i class="sort-arrow-down">▼</i>
                </span>
              </span>
            </th>
            <th class="sortable" @click="toggleSort('name')">
              <span class="th-content">
                Name
                <span class="sort-indicator" :class="getSortIndicatorClass('name')">
                  <i class="sort-arrow-up">▲</i>
                  <i class="sort-arrow-down">▼</i>
                </span>
              </span>
            </th>
            <th class="sortable" @click="toggleSort('price')">
              <span class="th-content">
                Price
                <span class="sort-indicator" :class="getSortIndicatorClass('price')">
                  <i class="sort-arrow-up">▲</i>
                  <i class="sort-arrow-down">▼</i>
                </span>
              </span>
            </th>
            <th class="sortable" @click="toggleSort('ter')">
              <span class="th-content">
                TER
                <span class="sort-indicator" :class="getSortIndicatorClass('ter')">
                  <i class="sort-arrow-up">▲</i>
                  <i class="sort-arrow-down">▼</i>
                </span>
              </span>
            </th>
            <th class="sortable" @click="toggleSort('annualReturn')">
              <span class="th-content">
                Annual Return
                <span class="sort-indicator" :class="getSortIndicatorClass('annualReturn')">
                  <i class="sort-arrow-up">▲</i>
                  <i class="sort-arrow-down">▼</i>
                </span>
              </span>
            </th>
            <th
              v-if="showRebalanceColumns"
              class="sortable"
              style="width: 130px"
              @click="toggleSort('currentPercent')"
            >
              <span class="th-content">
                Current
                <span class="sort-indicator" :class="getSortIndicatorClass('currentPercent')">
                  <i class="sort-arrow-up">▲</i>
                  <i class="sort-arrow-down">▼</i>
                </span>
              </span>
            </th>
            <th class="sortable" style="width: 150px" @click="toggleSort('value')">
              <span class="th-content">
                {{ showRebalanceColumns ? 'Target %' : 'Allocation %' }}
                <span class="sort-indicator" :class="getSortIndicatorClass('value')">
                  <i class="sort-arrow-up">▲</i>
                  <i class="sort-arrow-down">▼</i>
                </span>
              </span>
            </th>
            <th
              v-if="showInvestmentColumns || showRebalanceActionColumn"
              class="sortable"
              style="width: 90px"
              @click="toggleSort('units')"
            >
              <span class="th-content">
                {{ showRebalanceColumns ? 'Action' : 'Units' }}
                <span class="sort-indicator" :class="getSortIndicatorClass('units')">
                  <i class="sort-arrow-up">▲</i>
                  <i class="sort-arrow-down">▼</i>
                </span>
              </span>
            </th>
            <th
              v-if="showInvestmentColumns || showRebalanceActionColumn"
              class="sortable"
              style="width: 90px; white-space: nowrap"
              @click="toggleSort('afterPercent')"
            >
              <span class="th-content">
                {{ showRebalanceColumns ? 'After %' : 'Unused' }}
                <span class="sort-indicator" :class="getSortIndicatorClass('afterPercent')">
                  <i class="sort-arrow-up">▲</i>
                  <i class="sort-arrow-down">▼</i>
                </span>
              </span>
            </th>
            <th style="width: 50px"></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="allocation in sortedAllocations"
            :key="allocation.instrumentId || getOriginalIndex(allocation)"
          >
            <td>
              <select
                :value="allocation.instrumentId"
                class="form-select form-select-sm"
                @change="onInstrumentChange(getOriginalIndex(allocation), $event)"
              >
                <option :value="0" disabled>Select ETF</option>
                <option
                  v-for="etf in availableEtfsForRow(getOriginalIndex(allocation))"
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
            <td v-if="showRebalanceColumns" class="text-muted small">
              €{{ (allocation.currentValue ?? 0).toFixed(2) }}
              <span class="current-percent">
                ({{ getRebalanceData(allocation).currentPercent.toFixed(1) }}%)
              </span>
            </td>
            <td>
              <input
                :value="allocation.value"
                type="number"
                class="form-control form-control-sm"
                min="0"
                max="100"
                step="1"
                @input="onValueChange(getOriginalIndex(allocation), $event)"
              />
            </td>
            <td v-if="showInvestmentColumns || showRebalanceActionColumn" class="small">
              <template v-if="showRebalanceColumns">
                <span
                  v-if="hasRebalanceAction(allocation)"
                  :class="getRebalanceData(allocation).isBuy ? 'text-success' : 'text-danger'"
                >
                  {{ getRebalanceData(allocation).isBuy ? 'Buy' : 'Sell' }}
                  {{ formatActionValue(allocation) }}
                </span>
                <span v-else class="text-muted">-</span>
              </template>
              <template v-else>
                {{
                  formatAction(
                    allocation.instrumentId,
                    allocation.value,
                    getEtfPrice(allocation.instrumentId)
                  )
                }}
              </template>
            </td>
            <td v-if="showInvestmentColumns || showRebalanceActionColumn" class="text-muted small">
              <template v-if="showRebalanceColumns">
                {{ getAfterPercent(allocation).toFixed(1) }}%
              </template>
              <template v-else>
                {{
                  formatUnused(
                    allocation.instrumentId,
                    allocation.value,
                    getEtfPrice(allocation.instrumentId)
                  )
                }}
              </template>
            </td>
            <td>
              <button
                type="button"
                class="remove-btn"
                aria-label="Remove allocation"
                :disabled="allocations.length <= 1"
                @click="$emit('remove', getOriginalIndex(allocation))"
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
            {{ `${totalAllocation.toFixed(1)}%` }}
            <span v-if="!isValidTotal" class="total-hint">(should be 100%)</span>
          </span>
        </div>
        <div v-if="showInvestmentColumns || showRebalanceActionColumn" class="total-row">
          <span class="total-label">Total Unused</span>
          <span class="total-value text-muted">€{{ totalUnused.toFixed(2) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { formatTer, formatReturn } from '../../utils/formatters'
import { formatPlatformName } from '../../utils/platform-utils'
import { calculateInvestmentAmount } from '../../utils/diversification-calculations'
import { useSortableTable } from '../../composables/use-sortable-table'
import { useAllocationCalculations } from '../../composables/use-allocation-calculations'
import AllocationCard from './allocation-card.vue'
import type { EtfDetailDto } from '../../models/generated/domain-models'
import type { AllocationInput, ActionDisplayMode } from './types'

interface AllocationWithData extends AllocationInput {
  symbol: string
  name: string
  price: number | null
  ter: number | null
  annualReturn: number | null
  currentPercent: number
  units: number
  afterPercent: number
}

const props = defineProps<{
  allocations: AllocationInput[]
  availableEtfs: EtfDetailDto[]
  isLoadingPortfolio: boolean
  totalInvestment: number
  selectedPlatform: string | null
  availablePlatforms: string[]
  currentHoldingsTotal: number
  optimizeEnabled: boolean
  actionDisplayMode: ActionDisplayMode
}>()

const emit = defineEmits<{
  'update:allocation': [index: number, allocation: AllocationInput]
  'update:totalInvestment': [value: number]
  'update:selectedPlatform': [value: string | null]
  'update:optimizeEnabled': [value: boolean]
  'update:actionDisplayMode': [value: ActionDisplayMode]
  add: []
  remove: [index: number]
  clear: []
  loadPortfolio: []
  export: []
  import: []
}>()

const {
  getEtfName,
  getEtfPrice,
  getEtfTer,
  getEtfReturn,
  getEtfSymbol,
  showInvestmentColumns,
  showRebalanceColumns,
  showRebalanceActionColumn,
  getBaseRebalanceData,
  getRebalanceData,
  getAfterPercent,
  getAfterPercentForSort,
  getUnits,
  getUnused,
  getRebalanceAmount,
  getRebalanceFractionalAmount,
  hasRebalanceAction,
  formatActionValue,
  formatAction,
  formatUnused,
  totalUnused,
} = useAllocationCalculations(props)

const formatEtfPrice = (value: number | null) => (value === null ? '-' : `€${value.toFixed(2)}`)

const totalAllocation = computed(() =>
  props.allocations.reduce((sum, a) => sum + (a.value || 0), 0)
)

const isValidTotal = computed(() => Math.abs(totalAllocation.value - 100) < 0.1)

const availableEtfsForRow = (rowIndex: number) => {
  const selectedIds = props.allocations
    .filter((_, i) => i !== rowIndex)
    .map(a => a.instrumentId)
    .filter(id => id > 0)
  return props.availableEtfs.filter(etf => !selectedIds.includes(etf.instrumentId))
}

const getActionSortValue = (a: AllocationInput, base: ReturnType<typeof getBaseRebalanceData>) => {
  if (showRebalanceColumns.value) return base.difference
  if (props.actionDisplayMode === 'amount') {
    return calculateInvestmentAmount(props.totalInvestment, a.value)
  }
  return base.units
}

const allocationsWithData = computed<AllocationWithData[]>(() =>
  props.allocations.map(a => {
    const base = getBaseRebalanceData(a)
    return {
      ...a,
      symbol: getEtfSymbol(a.instrumentId),
      name: getEtfName(a.instrumentId),
      price: getEtfPrice(a.instrumentId),
      ter: getEtfTer(a.instrumentId),
      annualReturn: getEtfReturn(a.instrumentId),
      currentPercent: base.currentPercent,
      units: getActionSortValue(a, base),
      afterPercent: getAfterPercentForSort(a),
    }
  })
)

const { sortedItems, sortState, toggleSort } = useSortableTable(
  allocationsWithData,
  undefined,
  'asc'
)

const sortedAllocations = computed(() =>
  sortedItems.value.map(item => {
    const original = props.allocations.find(a => a.instrumentId === item.instrumentId)
    return original ?? item
  })
)

const getSortIndicatorClass = (key: string) => ({
  active: sortState.value.key === key,
  asc: sortState.value.key === key && sortState.value.direction === 'asc',
  desc: sortState.value.key === key && sortState.value.direction === 'desc',
})

const getOriginalIndex = (allocation: AllocationInput): number =>
  props.allocations.findIndex(a => a.instrumentId === allocation.instrumentId || a === allocation)

const onPlatformChange = (event: Event) => {
  const target = event.target as HTMLSelectElement
  emit('update:selectedPlatform', target.value || null)
}

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

.allocation-table th.sortable {
  cursor: pointer;
  user-select: none;
}

.allocation-table th.sortable:hover {
  background-color: var(--bs-gray-100);
}

.th-content {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.sort-indicator {
  display: inline-flex;
  flex-direction: column;
  font-size: 0.5rem;
  line-height: 1;
  opacity: 0.3;
  margin-left: 0.25rem;
}

.sort-indicator.active {
  opacity: 1;
}

.sort-indicator .sort-arrow-up,
.sort-indicator .sort-arrow-down {
  display: block;
  transition: opacity 0.15s ease;
}

.sort-indicator.asc .sort-arrow-up {
  opacity: 1;
}

.sort-indicator.asc .sort-arrow-down {
  opacity: 0.3;
}

.sort-indicator.desc .sort-arrow-up {
  opacity: 0.3;
}

.sort-indicator.desc .sort-arrow-down {
  opacity: 1;
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

.platform-selector {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.platform-selector label {
  font-size: 0.75rem;
  color: var(--bs-gray-600);
  white-space: nowrap;
}

.platform-selector .form-select {
  width: 130px;
  font-size: 0.875rem;
}

.current-holdings {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.current-holdings label {
  font-size: 0.75rem;
  color: var(--bs-gray-600);
  white-space: nowrap;
}

.holdings-value {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--bs-gray-800);
}

.current-percent {
  color: var(--bs-gray-500);
  margin-left: 0.25rem;
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

.display-mode-toggle {
  display: flex;
  gap: 0;
}

.display-mode-btn {
  padding: 0.25rem 0.5rem;
  border: 1px solid var(--bs-gray-300);
  background: var(--bs-white);
  color: var(--bs-gray-600);
  font-size: 0.6875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.12s ease;
  white-space: nowrap;
}

.display-mode-btn:first-child {
  border-radius: 0.25rem 0 0 0.25rem;
  border-right: none;
}

.display-mode-btn:last-child {
  border-radius: 0 0.25rem 0.25rem 0;
}

.display-mode-btn:hover:not(.active) {
  background: var(--bs-gray-100);
  border-color: var(--bs-gray-400);
  color: var(--bs-gray-700);
}

.display-mode-btn.active {
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

  .header-row {
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .investment-row {
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .platform-selector {
    flex: 1;
    min-width: 120px;
  }

  .platform-selector .form-select {
    width: 100%;
  }

  .current-holdings {
    flex-shrink: 0;
  }

  .current-holdings .holdings-value {
    font-size: 0.8125rem;
  }

  .total-investment-input {
    flex: 1;
    min-width: 100px;
  }

  .total-investment-input .input-group {
    width: 100%;
    flex-wrap: nowrap;
  }

  .optimize-toggle {
    flex-shrink: 0;
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

@media (max-width: 480px) {
  .investment-row {
    gap: 0.5rem;
  }

  .platform-selector {
    flex-basis: calc(50% - 0.25rem);
    min-width: 0;
  }

  .current-holdings {
    flex-basis: calc(50% - 0.25rem);
    justify-content: flex-end;
  }

  .total-investment-input {
    flex-basis: calc(70% - 0.25rem);
  }

  .optimize-toggle {
    flex-basis: calc(30% - 0.25rem);
    justify-content: flex-end;
  }
}
</style>
