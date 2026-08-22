<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3 pb-20 md:pb-0">
    <div class="mb-6 flex items-center justify-between">
      <h2 class="mb-0">Investment Calculator</h2>
    </div>

    <div class="grid grid-cols-12 gap-x-6">
      <div class="col-span-12 md:col-span-4">
        <form @submit.prevent>
          <div v-for="(field, key) in formFields" :key="key" class="mb-4">
            <label :for="key" class="form-label">{{ field.label }}:</label>
            <input
              v-model.number="form[key as keyof typeof form]"
              :id="key"
              :type="field.type || 'text'"
              class="form-control"
              :step="field.step"
              :min="field.min"
              required
              @wheel="blurOnWheel"
            />
          </div>
          <div class="calculator-buttons-desktop">
            <AppButton variant="secondary" size="sm" ghost @click="handleReset">
              Reset Calculator
            </AppButton>
          </div>
        </form>
      </div>
      <div class="col-span-12 md:col-span-8">
        <LoadingSpinner v-if="isLoading" />
        <LineChart
          v-if="!isLoading && portfolioData.length > 0"
          :data="portfolioData"
          title="Portfolio Growth Over Time"
          x-axis-label="Year"
          y-axis-label="Worth (€)"
        />
        <div v-if="!isLoading && calculationResult" class="card mt-4 xirr-stats-card">
          <div class="card-body py-2!">
            <div class="grid grid-cols-2 gap-x-6 text-center">
              <div>
                <div class="stat-label">Median XIRR</div>
                <div class="stat-value">{{ formatPercentage(calculationResult.median) }}</div>
              </div>
              <div>
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
          class="mt-4"
        />
      </div>
    </div>

    <div class="calculator-buttons-mobile">
      <AppButton variant="secondary" ghost @click="handleReset">Reset Calculator</AppButton>
    </div>

    <div class="mt-6">
      <h5>Year-by-Year Summary</h5>
      <data-table :items="yearSummary" :columns="summaryColumns" data-testid="year-summary-table" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useCalculator } from '../composables/use-calculator'
import { blurOnWheel } from '../utils/dom'
import { formatCurrency } from '../utils/formatters'
import LineChart from './charts/line-chart.vue'
import BarChart from './charts/bar-chart.vue'
import LoadingSpinner from './shared/loading-spinner.vue'
import DataTable, { type ColumnDefinition } from './shared/data-table.vue'
import AppButton from './shared/app-button.vue'
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
    confirmClass: 'btn-danger',
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

<style scoped>
canvas {
  width: 100% !important;
  height: auto !important;
}

.xirr-stats-card .card-body {
  padding: 0.75rem 1rem;
}

.stat-label {
  margin-bottom: 0.25rem;
  font-size: var(--text-label);
  font-weight: 500;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  color: var(--color-ink-soft);
}

.stat-value {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--color-brass-deep);
}

.calculator-buttons-desktop {
  display: flex;
  margin-top: 1rem;
  margin-bottom: 1.5rem;
}

.calculator-buttons-mobile {
  display: none;
}

@media (max-width: 767.98px) {
  .calculator-buttons-desktop {
    display: none;
  }

  .calculator-buttons-mobile {
    position: fixed;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: var(--z-sticky);
    display: flex;
    justify-content: center;
    padding: 1rem;
    background-color: var(--color-surface);
    box-shadow: 0 -2px 8px rgb(0 0 0 / 0.08);
  }

  .calculator-buttons-mobile .btn {
    width: 100%;
    max-width: 400px;
    font-size: 1rem;
  }

  .calculator-buttons-mobile .btn.btn-ghost {
    padding: 0.625rem 1.5rem;
  }
}
</style>
