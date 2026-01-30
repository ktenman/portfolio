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
        :input-mode="inputMode"
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
            ? Math.abs(
                ((currentHoldingsTotal + totalInvestment) * allocation.value) / 100 -
                  (allocation.currentValue ?? 0)
              )
            : (totalInvestment * allocation.value) / 100
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
                {{
                  inputMode === 'percentage'
                    ? showRebalanceColumns
                      ? 'Target %'
                      : 'Allocation %'
                    : 'Amount EUR'
                }}
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
              style="width: 90px"
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
                :max="inputMode === 'percentage' ? 100 : undefined"
                :step="inputMode === 'percentage' ? 1 : 100"
                @input="onValueChange(getOriginalIndex(allocation), $event)"
              />
            </td>
            <td v-if="showInvestmentColumns || showRebalanceActionColumn" class="small">
              <template v-if="showRebalanceColumns">
                <span
                  v-if="getRebalanceData(allocation).units > 0"
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
import { formatTer, formatReturn, formatCurrencyWithSymbol } from '../../utils/formatters'
import { formatPlatformName } from '../../utils/platform-utils'
import { useSortableTable } from '../../composables/use-sortable-table'
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
  inputMode: 'percentage' | 'amount'
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
  'update:inputMode': ['percentage' | 'amount']
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

const findEtf = (instrumentId: number) =>
  props.availableEtfs.find(e => e.instrumentId === instrumentId)

const getEtfName = (instrumentId: number) => findEtf(instrumentId)?.name || ''
const getEtfTer = (instrumentId: number) => findEtf(instrumentId)?.ter ?? null
const getEtfReturn = (instrumentId: number) => findEtf(instrumentId)?.annualReturn ?? null
const getEtfPrice = (instrumentId: number) => findEtf(instrumentId)?.currentPrice ?? null
const getEtfSymbol = (instrumentId: number) => findEtf(instrumentId)?.symbol || ''

const showInvestmentColumns = computed(
  () => props.inputMode === 'percentage' && props.totalInvestment > 0
)

const showRebalanceColumns = computed(() => !!props.selectedPlatform)

const showRebalanceActionColumn = computed(
  () => showRebalanceColumns.value && (props.totalInvestment > 0 || props.currentHoldingsTotal > 0)
)

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
      units: base.units,
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

const getBaseRebalanceData = (allocation: AllocationInput) => {
  const price = getEtfPrice(allocation.instrumentId)
  const currentValue = allocation.currentValue ?? 0
  const currentPercent =
    props.currentHoldingsTotal > 0 ? (currentValue / props.currentHoldingsTotal) * 100 : 0
  const targetPortfolio = props.currentHoldingsTotal + props.totalInvestment
  const targetValue = (targetPortfolio * allocation.value) / 100
  const difference = targetValue - currentValue
  const isBuy = difference >= 0
  const absoluteDifference = Math.abs(difference)
  const units = price && price > 0 ? Math.floor(absoluteDifference / price) : 0
  const actualAmount = units * (price ?? 0)
  const unused = absoluteDifference - actualAmount
  return { currentValue, currentPercent, targetValue, difference, isBuy, units, unused, price }
}

type RebalanceData = ReturnType<typeof getBaseRebalanceData>

const calcAfterValue = (allocation: AllocationInput, data: RebalanceData): number => {
  const currentValue = allocation.currentValue ?? 0
  const tradeValue = data.units * (data.price ?? 0)
  return data.isBuy ? currentValue + tradeValue : currentValue - tradeValue
}

const calcTotalAfterValue = (getData: (a: AllocationInput) => RebalanceData) =>
  props.allocations.reduce((sum, a) => sum + calcAfterValue(a, getData(a)), 0)

const totalAfterValueForSort = computed(() => calcTotalAfterValue(getBaseRebalanceData))

const getAfterPercentForSort = (allocation: AllocationInput): number => {
  if (props.actionDisplayMode === 'amount') return allocation.value
  if (totalAfterValueForSort.value <= 0) return 0
  const afterValue = calcAfterValue(allocation, getBaseRebalanceData(allocation))
  return (afterValue / totalAfterValueForSort.value) * 100
}

const onPlatformChange = (event: Event) => {
  const target = event.target as HTMLSelectElement
  emit('update:selectedPlatform', target.value || null)
}

const getRebalanceData = (allocation: AllocationInput): RebalanceData => {
  const base = getBaseRebalanceData(allocation)
  if (!props.optimizeEnabled || !optimizedRebalance.value.has(allocation.instrumentId)) {
    return base
  }
  const optimized = optimizedRebalance.value.get(allocation.instrumentId)!
  const actualAmount = optimized.units * (base.price ?? 0)
  const unused = Math.abs(base.difference) - actualAmount
  return { ...base, units: optimized.units, isBuy: optimized.isBuy, unused: Math.max(0, unused) }
}

const totalAfterValue = computed(() => calcTotalAfterValue(getRebalanceData))

const getAfterPercent = (allocation: AllocationInput): number => {
  if (props.actionDisplayMode === 'amount') return allocation.value
  if (totalAfterValue.value <= 0) return 0
  const afterValue = calcAfterValue(allocation, getRebalanceData(allocation))
  return (afterValue / totalAfterValue.value) * 100
}

const optimizedRebalanceResult = computed(() => {
  const emptyResult = {
    allocations: new Map<number, { units: number; isBuy: boolean }>(),
    totalRemaining: 0,
  }
  if (!showRebalanceColumns.value || !props.optimizeEnabled) {
    return emptyResult
  }
  const validAllocations = props.allocations.filter(a => a.instrumentId > 0 && a.value > 0)
  if (validAllocations.length === 0) return emptyResult
  const buyAllocations = validAllocations
    .map(a => ({ ...getBaseRebalanceData(a), id: a.instrumentId }))
    .filter(d => d.isBuy && d.difference > 0)
  if (buyAllocations.length === 0) {
    const result = new Map(
      validAllocations.map(a => {
        const base = getBaseRebalanceData(a)
        return [a.instrumentId, { units: base.units, isBuy: base.isBuy }]
      })
    )
    return { allocations: result, totalRemaining: 0 }
  }
  let totalUnused = buyAllocations.reduce((sum, d) => sum + d.unused, 0)
  const result = new Map(
    validAllocations.map(a => {
      const base = getBaseRebalanceData(a)
      return [a.instrumentId, { units: base.units, isBuy: base.isBuy }]
    })
  )
  const sortedByRemainder = [...buyAllocations]
    .filter(d => d.price && d.price > 0)
    .sort((a, b) => b.unused - a.unused)
  for (const fund of sortedByRemainder) {
    if (fund.price && fund.price <= totalUnused) {
      const current = result.get(fund.id)!
      result.set(fund.id, { ...current, units: current.units + 1 })
      totalUnused -= fund.price
    }
  }
  return { allocations: result, totalRemaining: Math.max(0, totalUnused) }
})

const optimizedRebalance = computed(() => optimizedRebalanceResult.value.allocations)

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
  if (!showInvestmentColumns.value || !props.optimizeEnabled) return new Map<number, number>()
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
  if (props.optimizeEnabled && optimizedAllocation.value.has(instrumentId)) {
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

const calculateRebalanceAmount = (allocation: AllocationInput): number => {
  const currentValue = allocation.currentValue ?? 0
  const targetPortfolio = props.currentHoldingsTotal + props.totalInvestment
  const targetValue = (targetPortfolio * allocation.value) / 100
  return Math.abs(targetValue - currentValue)
}

const calculateInvestmentAmount = (percentage: number): number =>
  (props.totalInvestment * percentage) / 100

const formatAmount = (amount: number): string => (amount === 0 ? '-' : `€${amount.toFixed(2)}`)

const formatUnused = (instrumentId: number, percentage: number, price: number | null): string => {
  if (props.actionDisplayMode === 'amount') return '-'
  const units = getUnits(instrumentId, percentage, price)
  if (units === 0) return '-'
  const unused = getUnused(instrumentId, percentage, price)
  return `€${unused.toFixed(2)}`
}

const formatActionValue = (allocation: AllocationInput): string => {
  if (props.actionDisplayMode === 'amount') {
    return formatAmount(calculateRebalanceAmount(allocation))
  }
  return getRebalanceData(allocation).units.toString()
}

const formatAction = (instrumentId: number, percentage: number, price: number | null): string => {
  if (props.actionDisplayMode === 'amount') {
    return formatAmount(calculateInvestmentAmount(percentage))
  }
  const units = getUnits(instrumentId, percentage, price)
  if (units === 0) return '-'
  return units.toString()
}

const totalUnused = computed(() => {
  if (!showInvestmentColumns.value && !showRebalanceActionColumn.value) return 0
  if (props.actionDisplayMode === 'amount') return 0
  if (showRebalanceColumns.value && props.optimizeEnabled) {
    return optimizedRebalanceResult.value.totalRemaining
  }
  if (showRebalanceColumns.value) {
    return props.allocations.reduce((sum, allocation) => {
      const data = getRebalanceData(allocation)
      return sum + (data.units > 0 ? data.unused : 0)
    }, 0)
  }
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
