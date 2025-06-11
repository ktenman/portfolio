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
              @input="handleInput(key)"
            />
          </div>
          <div class="calculator-buttons-desktop">
            <button
              type="button"
              class="btn btn-outline-secondary me-2 btn-sm"
              @click="resetCalculator"
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

    <!-- Buttons for mobile view, fixed at bottom of screen -->
    <div class="calculator-buttons-mobile">
      <button type="button" class="btn btn-outline-secondary me-2" @click="resetCalculator">
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
import { useFormatters } from '../composables/use-formatters'
import LineChart from './charts/line-chart.vue'
import BarChart from './charts/bar-chart.vue'
import LoadingSpinner from './shared/loading-spinner.vue'

const {
  form,
  isLoading,
  yearSummary,
  portfolioData,
  calculationResult,
  handleInput,
  resetCalculator,
} = useCalculator()

const { formatCurrency } = useFormatters()

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

<style scoped>
canvas {
  width: 100% !important;
  height: auto !important;
}

.table {
  font-size: 0.9rem;
}

/* Desktop button styling */
.calculator-buttons-desktop {
  display: flex;
  margin-top: 1rem;
  margin-bottom: 1.5rem;
}

/* Mobile button styling - hidden by default on desktop */
.calculator-buttons-mobile {
  display: none;
}

@media (max-width: 767px) {
  .table {
    font-size: 0.8rem;
  }

  /* Hide desktop buttons on mobile */
  .calculator-buttons-desktop {
    display: none;
  }

  /* Show and style mobile buttons */
  .calculator-buttons-mobile {
    display: flex;
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    background-color: white;
    padding: 10px 15px;
    box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.1);
    z-index: 1000;
    justify-content: space-between;
  }

  /* Make buttons equal width and larger */
  .calculator-buttons-mobile .btn {
    flex: 1;
    padding: 10px 12px;
    font-size: 14px;
  }

  .calculator-buttons-mobile .btn:first-child {
    margin-right: 10px;
  }

  /* Add bottom padding to container to prevent content being hidden behind fixed buttons */
  .container {
    padding-bottom: 60px;
  }
}
</style>
