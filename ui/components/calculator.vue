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
        <div v-if="!isLoading && calculationResult" class="card mt-3 xirr-stats-card">
          <div class="card-body py-2">
            <div class="row text-center">
              <div class="col-6">
                <div class="stat-label">Median XIRR</div>
                <div class="stat-value">{{ formatPercentage(calculationResult.median) }}</div>
              </div>
              <div class="col-6">
                <div class="stat-label">Average XIRR</div>
                <div class="stat-value">{{ formatPercentage(calculationResult.average) }}</div>
              </div>
            </div>
          </div>
        </div>
        <BarChart
          v-if="!isLoading && calculationResult && calculationResult.cashFlows.length > 0"
          :data="calculationResult.cashFlows"
          title="XIRR Rolling Result (ASAP)"
          x-axis-label="Date"
          y-axis-label="XIRR (%)"
          class="mt-3"
        />
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
import { useCalculator } from '../composables/use-calculator'
import { formatCurrency } from '../utils/formatters'
import LineChart from './charts/line-chart.vue'
import BarChart from './charts/bar-chart.vue'
import LoadingSpinner from './shared/loading-spinner.vue'
import DataTable, { type ColumnDefinition } from './shared/data-table.vue'
import { useConfirm } from '../composables/use-confirm'

const { form, isLoading, yearSummary, portfolioData, calculationResult, resetCalculator } =
  useCalculator()

const { confirm } = useConfirm()

const formatPercentage = (value: number) => {
  return `${value.toFixed(2)}%`
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

.xirr-stats-card {
  box-shadow: 0 1px 3px rgba($black, 0.1);

  .card-body {
    padding: 0.75rem 1rem;
  }

  .stat-label {
    font-size: rem(12px);
    color: $gray-600;
    font-weight: 500;
    margin-bottom: 0.25rem;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  .stat-value {
    font-size: rem(20px);
    font-weight: 600;
    color: rgb(75, 192, 192);
  }
}

.calculator-buttons-desktop {
  display: flex;
  margin-top: $spacing-md;
  margin-bottom: $spacing-lg;
}

.calculator-buttons-mobile {
  display: none;
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
