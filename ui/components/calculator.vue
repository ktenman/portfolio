<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <h4>Investment Calculator</h4>
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
              @input="handleInput()"
            />
          </div>
          <div class="calculator-buttons-desktop">
            <button
              type="button"
              class="btn btn-outline-secondary me-2 btn-sm"
              @click="handleReset"
            >
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
        <BarChart
          v-if="!isLoading && calculationResult"
          :data="calculationResult.xirrs"
          title="XIRR Rolling Result (ASAP)"
          x-axis-label="Date"
          y-axis-label="XIRR (%)"
          class="mt-4"
        />
      </div>
    </div>

    <div class="calculator-buttons-mobile">
      <button type="button" class="btn btn-outline-secondary me-2" @click="handleReset">
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

const {
  form,
  isLoading,
  yearSummary,
  portfolioData,
  calculationResult,
  handleInput,
  resetCalculator,
} = useCalculator()

const { confirm } = useConfirm()

const handleReset = async () => {
  const confirmed = await confirm({ message: 'Are you sure you want to reset the calculator?' })
  if (!confirmed) return

  resetCalculator()
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
  years: { label: 'Number of Years', step: '1', type: 'number', min: 1 },
}

const summaryColumns: ColumnDefinition[] = [
  { key: 'year', label: 'Year' },
  { key: 'totalWorth', label: 'Total Worth', formatter: formatCurrency },
  { key: 'yearGrowth', label: "Year's Growth", formatter: formatCurrency },
  { key: 'earningsPerMonth', label: 'Earnings Per Month', formatter: formatCurrency },
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
    padding: $spacing-sm $spacing-md;
    box-shadow: 0 -2px $spacing-sm rgba($black, 0.1);
    z-index: $zindex-dropdown;
    justify-content: space-between;

    .btn {
      flex: 1;
      padding: $spacing-sm $spacing-sm;
      font-size: $font-size-sm;

      &:first-child {
        margin-right: $spacing-sm;
      }
    }
  }

  .container {
    padding-bottom: rem(60px);
  }
}
</style>
