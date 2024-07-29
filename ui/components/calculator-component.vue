<template>
  <div class="container mt-2">
    <h4 class="mb-4">Investment Calculator</h4>
    <div v-if="isLoading" class="text-center">
      <div class="spinner-border" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>
    <div v-else class="row">
      <div class="col-md-4">
        <form @submit.prevent="calculate">
          <div class="mb-3" v-for="(_, key) in form" :key="key">
            <label :for="key" class="form-label">{{ getFieldLabel(key) }}:</label>
            <input
              :type="key === 'years' ? 'number' : 'text'"
              v-model="form[key]"
              :id="key"
              class="form-control"
              :step="getFieldStep(key)"
              :min="key === 'years' ? 1 : undefined"
              required
            />
          </div>
          <!--          <button type="submit" class="btn btn-primary">Recalculate</button>-->
        </form>
      </div>
      <div class="col-md-8">
        <canvas ref="portfolioChart"></canvas>
      </div>
    </div>
    <div v-if="!isLoading" class="row mt-4">
      <div class="col-12">
        <h5>Year-by-Year Summary</h5>
        <div class="table-responsive">
          <table class="table table-striped table-hover">
            <thead>
              <tr>
                <th>Year</th>
                <th>Total Worth</th>
                <th>Year's Growth</th>
                <th>Earnings Per Day</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(summary, index) in yearSummary" :key="index">
                <td>{{ summary.year }}</td>
                <td>{{ formatCurrency(summary.totalWorth) }}</td>
                <td>{{ formatCurrency(summary.yearGrowth) }}</td>
                <td>{{ formatCurrency(summary.earningsPerDay) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import Chart from 'chart.js/auto'
import { CalculatorService } from '../services/calculator-service.ts'

const calculatorService = new CalculatorService()

const form = reactive({
  initialWorth: 1000,
  monthlyInvestment: 2800,
  yearlyGrowthRate: 5,
  annualReturnRate: 0,
  years: 10,
})

const isLoading = ref(true)
const portfolioChart = ref<HTMLCanvasElement | null>(null)
const yearSummary = ref<
  Array<{
    year: number
    totalWorth: number
    yearGrowth: number
    earningsPerDay: number
  }>
>([])
let chart: Chart | null = null

const fetchXirr = async () => {
  try {
    const xirr = await calculatorService.getXirr()
    form.annualReturnRate = Number((xirr * 100).toFixed(3))
    isLoading.value = false
    await nextTick()
    calculate()
  } catch (error) {
    console.error('Error fetching XIRR:', error)
    isLoading.value = false
    // Handle error (e.g., show an error message to the user)
  }
}

const calculate = () => {
  const { initialWorth, monthlyInvestment, yearlyGrowthRate, annualReturnRate, years } = form
  const values: number[] = []
  let totalWorth = Math.max(0, parseFloat(initialWorth.toString()))
  let currentMonthlyInvestment = Math.max(0, parseFloat(monthlyInvestment.toString()))
  const growthRate = Math.max(0, parseFloat(yearlyGrowthRate.toString())) / 100
  const returnRate = Math.max(0, parseFloat(annualReturnRate.toString())) / 100
  const numYears = Math.max(1, parseInt(years.toString()))

  yearSummary.value = []

  for (let year = 1; year <= numYears; year++) {
    const yearStartWorth = totalWorth
    for (let month = 1; month <= 12; month++) {
      totalWorth += currentMonthlyInvestment
      totalWorth *= 1 + returnRate / 12
    }
    currentMonthlyInvestment *= 1 + growthRate
    values.push(totalWorth)

    const yearGrowth = totalWorth - yearStartWorth
    const earningsPerDay = (totalWorth * returnRate) / 365.25

    yearSummary.value.push({
      year,
      totalWorth,
      yearGrowth,
      earningsPerDay,
    })
  }

  nextTick(() => renderChart(values))
}

const renderChart = (data: number[]) => {
  if (!portfolioChart.value) return

  if (chart) {
    chart.destroy()
  }

  const ctx = portfolioChart.value.getContext('2d')
  if (ctx) {
    chart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: Array.from({ length: data.length }, (_, i) => i + 1),
        datasets: [
          {
            label: 'Portfolio Worth',
            data: data,
            borderColor: 'rgba(75, 192, 192, 1)',
            borderWidth: 2,
            fill: false,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: {
            title: { display: true, text: 'Year' },
            grid: { display: false },
          },
          y: {
            title: { display: true, text: 'Worth (€)' },
            ticks: { callback: value => '€' + value.toLocaleString() },
          },
        },
        plugins: {
          title: {
            display: true,
            text: 'Portfolio Growth Over Time',
            font: { size: 16 },
          },
          legend: { display: false },
        },
      },
    })
  }
}

const formatCurrency = (value: number) => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

const getFieldLabel = (key: string) => {
  const labels: { [key: string]: string } = {
    initialWorth: 'Initial Worth (€)',
    monthlyInvestment: 'Monthly Investment (€)',
    yearlyGrowthRate: 'Yearly Growth Rate (%)',
    annualReturnRate: 'Annual Return Rate (%)',
    years: 'Number of Years',
  }
  return labels[key] || key
}

const getFieldStep = (key: string) => {
  const steps: { [key: string]: string } = {
    initialWorth: '0.01',
    monthlyInvestment: '0.01',
    yearlyGrowthRate: '0.001',
    annualReturnRate: '0.001',
    years: '1',
  }
  return steps[key] || 'any'
}

onMounted(async () => {
  await fetchXirr()
})

watch(
  () => ({
    initialWorth: form.initialWorth,
    monthlyInvestment: form.monthlyInvestment,
    yearlyGrowthRate: form.yearlyGrowthRate,
    annualReturnRate: form.annualReturnRate,
    years: form.years,
  }),
  () => {
    if (!isLoading.value) {
      calculate()
    }
  },
  { deep: true }
)
</script>

<style scoped>
canvas {
  width: 100% !important;
  height: 400px !important;
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
