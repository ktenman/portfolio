<template>
  <div class="container mt-3">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <h2 class="mb-0">Investment Calculator</h2>
    </div>

    <div class="row">
      <div class="col-md-4">
        <form @submit.prevent>
          <div v-for="(field, key) in formFields" :key="key" class="mb-3">
            <label :for="key" class="form-label">{{ field.label }}:</label>
            <input
              v-model.number="form[key as keyof typeof form]"
              :id="key"
              :type="field.type || 'text'"
              class="form-control"
              :step="field.step"
              :min="field.min"
              required
            />
          </div>
          <div class="calculator-buttons-desktop">
            <button type="button" class="btn btn-ghost btn-sm btn-secondary" @click="handleReset">
              Reset Calculator
            </button>
          </div>
        </form>
        <div v-if="!isLoading && portfolioXirr" class="stats-container mt-4">
          <div class="stat-card">
            <div class="stat-label">Weighted XIRR</div>
            <div class="stat-value" :class="getXirrClass(portfolioXirr.portfolioWeightedXirr)">
              {{ formatPercentage(portfolioXirr.portfolioWeightedXirr) }}
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-label">Average XIRR</div>
            <div class="stat-value" :class="getXirrClass(portfolioXirr.portfolioAverageXirr)">
              {{ formatPercentage(portfolioXirr.portfolioAverageXirr) }}
            </div>
          </div>
        </div>
      </div>
      <div class="col-md-8">
        <LoadingSpinner v-if="isLoading" />
        <LineChart
          v-if="!isLoading && portfolioData.length > 0"
          :data="portfolioData"
          title="Portfolio Growth Over Time"
          x-axis-label="Year"
          y-axis-label="Worth (€)"
        />
      </div>
    </div>

    <div v-if="!isLoading && portfolioXirr" class="row mt-4">
      <div class="col-12">
        <h5>Instrument Performance</h5>
        <div v-if="portfolioXirr.instruments.length > 0" class="table-responsive">
          <table class="table table-hover">
            <thead>
              <tr>
                <th>Symbol</th>
                <th>Name</th>
                <th class="text-end">Current Value</th>
                <th class="text-end">Median XIRR</th>
                <th class="text-end">Weight</th>
                <th class="text-end">Contribution</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="instrument in sortedInstruments" :key="instrument.instrumentId">
                <tr
                  class="instrument-row"
                  :class="{ expanded: isRowExpanded(instrument.instrumentId) }"
                  @click="toggleRow(instrument.instrumentId)"
                >
                  <td>
                    <span class="expand-icon">
                      {{ isRowExpanded(instrument.instrumentId) ? '▼' : '▶' }}
                    </span>
                    {{ instrument.symbol }}
                  </td>
                  <td>{{ instrument.name }}</td>
                  <td class="text-end">{{ formatCurrency(instrument.currentValue) }}</td>
                  <td class="text-end" :class="getXirrClass(instrument.medianXirr)">
                    {{ formatPercentage(instrument.medianXirr) }}
                  </td>
                  <td class="text-end">{{ formatPercentage(instrument.portfolioWeight) }}</td>
                  <td class="text-end" :class="getXirrClass(instrument.weightedXirr)">
                    {{ formatPercentage(instrument.weightedXirr) }}
                  </td>
                </tr>
                <tr v-if="isRowExpanded(instrument.instrumentId)" class="chart-row">
                  <td colspan="6" class="p-0">
                    <div class="chart-container">
                      <BarChart
                        :data="getReversedXirrs(instrument)"
                        x-axis-label="Date"
                        y-axis-label="XIRR (%)"
                      />
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="calculator-buttons-mobile">
      <button type="button" class="btn btn-ghost btn-secondary" @click="handleReset">
        Reset Calculator
      </button>
    </div>

    <div class="row mt-4">
      <div class="col-12">
        <h5>Year-by-Year Summary</h5>
        <data-table :items="yearSummary" :columns="summaryColumns" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useCalculator } from '../composables/use-calculator'
import { formatCurrency } from '../utils/formatters'
import LineChart from './charts/line-chart.vue'
import BarChart from './charts/bar-chart.vue'
import LoadingSpinner from './shared/loading-spinner.vue'
import DataTable, { type ColumnDefinition } from './shared/data-table.vue'
import { useConfirm } from '../composables/use-confirm'
import type { InstrumentRollingXirrDto } from '../models/generated/domain-models'

const { form, isLoading, yearSummary, portfolioData, portfolioXirr, resetCalculator } =
  useCalculator()

const { confirm } = useConfirm()
const expandedRows = ref<Set<number>>(new Set())

const formatPercentage = (value: number) => {
  return `${value.toFixed(2)}%`
}

const sortedInstruments = computed(() => {
  if (!portfolioXirr.value?.instruments) return []
  return [...portfolioXirr.value.instruments].sort((a, b) => b.currentValue - a.currentValue)
})

const toggleRow = (instrumentId: number) => {
  if (expandedRows.value.has(instrumentId)) {
    expandedRows.value.delete(instrumentId)
  } else {
    expandedRows.value.add(instrumentId)
  }
}

const isRowExpanded = (instrumentId: number) => expandedRows.value.has(instrumentId)

const getXirrClass = (xirr: number) => (xirr >= 0 ? 'text-success' : 'text-danger')

const getReversedXirrs = (instrument: InstrumentRollingXirrDto) => {
  return [...instrument.rollingXirrs].reverse()
}

const handleReset = async () => {
  const confirmed = await confirm({
    title: 'Reset Calculator',
    message: 'Are you sure you want to reset the calculator to default values?',
    confirmText: 'Reset',
    cancelText: 'Cancel',
    confirmClass: 'btn-warning',
  })

  if (confirmed) {
    await resetCalculator()
  }
}

interface FormField {
  label: string
  step: string
  type?: string
  min?: number
}

const formFields: Record<string, FormField> = {
  initialWorth: { label: 'Initial Worth (€)', step: '0.01' },
  monthlyInvestment: { label: 'Monthly Investment (€)', step: '0.01' },
  yearlyGrowthRate: { label: 'Yearly Growth Rate (%)', step: '0.001' },
  annualReturnRate: { label: 'Annual Return Rate (%)', step: '0.001' },
  taxRate: { label: 'Tax Rate (%)', step: '0.01' },
  years: { label: 'Number of Years', step: '1', type: 'number', min: 1 },
}

const formatTaxAmount = (value: number) => {
  return value === 0 ? '' : formatCurrency(value)
}

const summaryColumns: ColumnDefinition[] = [
  { key: 'year', label: 'Year' },
  { key: 'totalInvested', label: 'Total Invested', formatter: formatCurrency },
  { key: 'grossProfit', label: 'Gross Profit', formatter: formatCurrency },
  { key: 'totalWorth', label: 'Total Worth', formatter: formatCurrency },
  { key: 'taxAmount', label: 'Tax Amount', formatter: formatTaxAmount },
  { key: 'netWorth', label: 'Net Worth', formatter: formatCurrency },
  { key: 'monthlyEarnings', label: 'Monthly Earnings', formatter: formatCurrency },
]
</script>

<style lang="scss" scoped>
@use '../styles/config' as *;

canvas {
  width: 100% !important;
  height: auto !important;
}

.table {
  font-size: rem(14.4px);
}

.stats-container {
  display: flex;
  flex-direction: row;
  gap: 0.75rem;
}

.stat-card {
  background: $white;
  border: 1px solid $gray-300;
  padding: 1rem 1.5rem;
  border-radius: 0.5rem;
  box-shadow: 0 1px 3px rgba($black, 0.1);
  flex: 1;
}

.stat-label {
  font-size: rem(12px);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: $gray-600;
  font-weight: 500;
  margin-bottom: 0.25rem;
}

.stat-value {
  font-size: rem(24px);
  font-weight: 700;
  line-height: 1.2;
}

.calculator-buttons-desktop {
  display: flex;
  margin-top: $spacing-md;
  margin-bottom: $spacing-lg;
}

.calculator-buttons-mobile {
  display: none;
}

.instrument-row {
  cursor: pointer;
  @include transition(background-color);

  &:hover {
    background-color: rgba($primary-color, 0.05);
  }

  &.expanded {
    background-color: rgba($primary-color, 0.1);
  }
}

.expand-icon {
  display: inline-block;
  width: 1rem;
  margin-right: 0.5rem;
  font-size: 0.75rem;
  color: $gray-600;
}

.chart-row {
  > td {
    background-color: $gray-100;
    border-top: none !important;
    padding: 0 !important;
  }
}

.chart-container {
  padding: 1rem 1.5rem;
  background-color: $white;
  border: 1px solid $gray-200;
  border-radius: 0.5rem;
  margin: 0.75rem;
  height: 320px;

  canvas {
    width: 100% !important;
    height: 100% !important;
  }
}

@include media-breakpoint-down(md) {
  .table {
    font-size: rem(12.8px);
  }

  .calculator-buttons-desktop {
    display: none;
  }

  .calculator-buttons-mobile {
    display: flex;
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    background-color: $white;
    padding: $spacing-md;
    box-shadow: 0 -2px $spacing-sm rgba($black, 0.1);
    z-index: $zindex-sticky;
    justify-content: center;

    .btn {
      width: 100%;
      max-width: 400px;
      padding: $spacing-sm $spacing-lg;
      font-size: $font-size-base;

      &.btn-ghost {
        padding: 0.625rem 1.5rem;
      }
    }
  }

  .container {
    padding-bottom: rem(80px);
  }
}
</style>
