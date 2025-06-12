<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <h4>Investment Calculator</h4>
    </div>

    <div class="row">
      <div class="col-md-4">
        <form @submit.prevent>
          <div v-for="(label, key) in labels" :key="key" class="mb-3">
            <label :for="key" class="form-label">{{ label }}:</label>
            <input
              v-model.number="form[key as keyof typeof form]"
              :id="key"
              :type="key === 'years' ? 'number' : 'text'"
              class="form-control"
              :step="steps[key]"
              :min="key === 'years' ? 1 : undefined"
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
        <div class="table-responsive">
          <table class="table table-striped table-hover">
            <thead>
              <tr>
                <th v-for="header in tableHeaders" :key="header">{{ header }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="summary in yearSummary" :key="summary.year">
                <td>{{ summary.year }}</td>
                <td v-for="key in ['totalWorth', 'yearGrowth', 'earningsPerMonth']" :key="key">
                  {{ formatCurrency(summary[key as keyof typeof summary]) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
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

const labels = {
  initialWorth: 'Initial Worth (€)',
  monthlyInvestment: 'Monthly Investment (€)',
  yearlyGrowthRate: 'Yearly Growth Rate (%)',
  annualReturnRate: 'Annual Return Rate (%)',
  years: 'Number of Years',
}

const steps = {
  initialWorth: '0.01',
  monthlyInvestment: '0.01',
  yearlyGrowthRate: '0.001',
  annualReturnRate: '0.001',
  years: '1',
}

const tableHeaders = ['Year', 'Total Worth', "Year's Growth", 'Earnings Per Month']
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
