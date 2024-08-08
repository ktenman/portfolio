<template>
  <div class="container mt-2">
    <h4 class="mb-4">Investment Calculator</h4>
    <div class="row">
      <div class="col-md-4">
        <form @submit.prevent="calculate">
          <div v-for="(label, key) in labels" :key="key" class="mb-3">
            <label :for="key" class="form-label">{{ label }}:</label>
            <input
              v-model.number="form[key]"
              :id="key"
              :type="key === 'years' ? 'number' : 'text'"
              class="form-control"
              :step="steps[key]"
              :min="key === 'years' ? 1 : undefined"
              required
            />
          </div>
          <button type="submit" class="btn btn-primary">Recalculate</button>
        </form>
      </div>
      <div class="col-md-8">
        <div v-if="isLoading" class="d-flex justify-content-center align-items-center">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
        <canvas ref="chart"></canvas>
        <canvas ref="resultChart" class="mt-4"></canvas>
      </div>
    </div>
    <div class="row mt-4">
      <div class="col-12">
        <h5>Year-by-Year Summary</h5>
        <table class="table table-striped table-hover">
          <thead>
            <tr>
              <th v-for="header in tableHeaders" :key="header">{{ header }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="summary in yearSummary" :key="summary.year">
              <td>{{ summary.year }}</td>
              <td v-for="key in ['totalWorth', 'yearGrowth', 'earningsPerDay']" :key="key">
                {{ formatCurrency(summary[key]) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import Chart from 'chart.js/auto'
import { CalculationService } from '../services/calculation-service.ts'
import { CalculationResult } from '../models/calculation-result.ts'

const form = reactive({
  initialWorth: 1000,
  monthlyInvestment: 2800,
  yearlyGrowthRate: 5,
  annualReturnRate: 25.341,
  years: 10,
})

const labels = {
  initialWorth: 'Initial Worth (€)',
  monthlyInvestment: 'Monthly Investment (€)',
  yearlyGrowthRate: 'Yearly Growth Rate (%)',
  annualReturnRate: 'Annual Return Rate (%)',
  years: 'Number of Years',
}

const isLoading = ref(true)
const steps = {
  initialWorth: '0.01',
  monthlyInvestment: '0.01',
  yearlyGrowthRate: '0.001',
  annualReturnRate: '0.001',
  years: '1',
}

const tableHeaders = ['Year', 'Total Worth', "Year's Growth", 'Earnings Per Day']

const calculationService = new CalculationService()

const chart = ref<HTMLCanvasElement | null>(null)
const resultChart = ref<HTMLCanvasElement | null>(null)
const yearSummary = ref<Array<Record<string, number>>>([])
let chartInstance: Chart | null = null
let resultChartInstance: Chart | null = null

const calculate = async () => {
  const calculationResult = await fetchCalculationResult()
  form.annualReturnRate = Number(
    Number(calculationResult.xirrs[calculationResult.xirrs.length - 1].amount).toFixed(3)
  )

  const { initialWorth, monthlyInvestment, yearlyGrowthRate, annualReturnRate, years } = form
  const values = []
  let totalWorth = initialWorth
  let currentMonthlyInvestment = monthlyInvestment

  yearSummary.value = []

  for (let year = 1; year <= years; year++) {
    const yearStartWorth = totalWorth
    for (let month = 1; month <= 12; month++) {
      totalWorth += currentMonthlyInvestment
      totalWorth *= 1 + annualReturnRate / 1200
    }
    currentMonthlyInvestment *= 1 + yearlyGrowthRate / 100
    values.push(totalWorth)

    yearSummary.value.push({
      year,
      totalWorth,
      yearGrowth: totalWorth - yearStartWorth,
      earningsPerDay: (totalWorth * annualReturnRate) / 36525,
    })
  }

  renderChart(values)
  renderResultChart(calculationResult)
}

const renderChart = (data: number[]) => {
  if (chartInstance) chartInstance.destroy()
  const ctx = chart.value?.getContext('2d')
  if (ctx) {
    chartInstance = new Chart(ctx, {
      type: 'line',
      data: {
        labels: Array.from({ length: data.length }, (_, i) => i + 1),
        datasets: [
          {
            label: 'Portfolio Worth',
            data,
            borderColor: 'rgba(75, 192, 192, 1)',
            borderWidth: 2,
            fill: false,
          },
        ],
      },
      options: {
        responsive: true,
        scales: {
          x: { title: { display: true, text: 'Year' }, grid: { display: false } },
          y: {
            title: { display: true, text: 'Worth (€)' },
            ticks: { callback: value => '€' + (value as number).toLocaleString() },
          },
        },
        plugins: {
          title: { display: true, text: 'Portfolio Growth Over Time', font: { size: 16 } },
          legend: { display: false },
        },
      },
    })
  }
}

const renderResultChart = (result: CalculationResult) => {
  if (resultChartInstance) resultChartInstance.destroy()
  const ctx = resultChart.value?.getContext('2d')
  if (ctx) {
    resultChartInstance = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: result.xirrs.map(x => x.date),
        datasets: [
          {
            label: 'XIRR',
            data: result.xirrs.map(x => x.amount),
            backgroundColor: 'rgba(153, 102, 255, 0.6)',
            borderColor: 'rgba(153, 102, 255, 1)',
            borderWidth: 1,
          },
        ],
      },
      options: {
        responsive: true,
        scales: {
          x: { title: { display: true, text: 'Date' }, grid: { display: false } },
          y: {
            title: { display: true, text: 'XIRR (%)' },
            ticks: {
              callback: value => '' + (value as number).toLocaleString() + '%',
              stepSize: 5, // Ticks every 5%
            },
          },
        },
        plugins: {
          title: { display: true, text: 'XIRR Rolling Result', font: { size: 16 } },
          legend: { display: false },
        },
      },
    })
  }
}

const fetchCalculationResult = async (): Promise<CalculationResult> => {
  try {
    return await calculationService.fetchCalculationResult()
  } finally {
    isLoading.value = false
  }
}

const formatCurrency = (value: number) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
  }).format(value)

onMounted(calculate)
watch(form, calculate, { deep: true })
</script>

<style scoped>
canvas {
  width: 100% !important;
  height: auto !important;
}

.table {
  font-size: 0.9rem;
}

@media (max-width: 767px) {
  .table {
    font-size: 0.8rem;
  }
}
</style>
