<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <h4>Investment Calculator</h4>
      <div>
        <button
          type="button"
          class="btn btn-outline-secondary me-2 btn-sm"
          @click="resetCalculator"
        >
          Reset Calculator
        </button>
        <button type="button" class="btn btn-primary btn-sm" @click="calculate">Calculate</button>
      </div>
    </div>

    <div class="row">
      <div class="col-md-4">
        <form @submit.prevent="calculate">
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
          <div class="form-text mb-3">
            <small>Your data is automatically saved in your browser</small>
          </div>
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
                  {{ formatCurrency(summary[key]) }}
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
import { ref, onMounted, watch, reactive, nextTick, toRaw } from 'vue'
import Chart from 'chart.js/auto'
import { CalculationService } from '../services/calculation-service.ts'
import { CalculationResult } from '../models/calculation-result.ts'

const defaultForm = {
  initialWorth: 2000,
  monthlyInvestment: 585,
  yearlyGrowthRate: 5,
  annualReturnRate: 21.672,
  years: 28,
}

const STORAGE_KEY = 'investment-calculator-form'

const form = reactive({ ...defaultForm })

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

const tableHeaders = ['Year', 'Total Worth', "Year's Growth", 'Earnings Per Month']

const calculationService = new CalculationService()

const chart = ref<HTMLCanvasElement | null>(null)
const resultChart = ref<HTMLCanvasElement | null>(null)
const yearSummary = ref<Array<Record<string, number>>>([])
let chartInstance: Chart | null = null
let resultChartInstance: Chart | null = null

const isUpdatingForm = ref(false)
const formChanges = ref<Record<string, boolean>>({})

const loadFromLocalStorage = () => {
  try {
    const savedForm = localStorage.getItem(STORAGE_KEY)
    if (savedForm) {
      const parsedForm = JSON.parse(savedForm)

      // Apply saved values to form
      Object.assign(form, parsedForm)

      // Mark all fields loaded from localStorage as manually changed
      Object.keys(parsedForm).forEach(key => {
        formChanges.value[key] = true
      })
    }
  } catch (error) {
    console.error('Error loading form data from localStorage:', error)
  }
}

const saveToLocalStorage = () => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(toRaw(form)))
  } catch (error) {
    console.error('Error saving form data to localStorage:', error)
  }
}

const handleInput = (field: string) => {
  // Mark field as manually changed
  formChanges.value[field] = true
  // Save the entire form to localStorage
  saveToLocalStorage()
}

const resetCalculator = () => {
  if (
    confirm(
      'Are you sure you want to reset the calculator? This will clear all your current values.'
    )
  ) {
    Object.keys(form).forEach(key => {
      form[key as keyof typeof form] = defaultForm[key as keyof typeof defaultForm]
    })
    // Clear all change flags when resetting
    formChanges.value = {}
    localStorage.removeItem(STORAGE_KEY)
    calculate()
  }
}

const calculate = async () => {
  isLoading.value = true

  if (isUpdatingForm.value) return

  try {
    const calculationResult = await fetchCalculationResult()

    try {
      isUpdatingForm.value = true

      // Only update fields that haven't been manually changed
      if (!formChanges.value.annualReturnRate) {
        form.annualReturnRate = Number(calculationResult.average.toFixed(3))
      }

      if (!formChanges.value.initialWorth) {
        form.initialWorth = Number(calculationResult.total.toFixed(2))
      }

      // Always save the current state to localStorage
      saveToLocalStorage()
    } finally {
      await nextTick(() => {
        isUpdatingForm.value = false
      })
    }

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
        earningsPerMonth: (totalWorth * annualReturnRate) / 1200,
      })
    }

    renderChart(values)
    renderResultChart(calculationResult)
  } finally {
    isLoading.value = false
  }
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

const applyASAP = (data: { date: string; amount: number }[], maxPoints: number) => {
  if (data.length <= maxPoints) return data

  const step = Math.floor(data.length / maxPoints)
  const result = []

  for (let i = 0; i < data.length; i += step) {
    const chunk = data.slice(i, Math.min(i + step, data.length))
    const avgAmount = chunk.reduce((sum, item) => sum + item.amount, 0) / chunk.length
    result.push({ date: chunk[0].date, amount: avgAmount })
  }

  if (result[result.length - 1].date !== data[data.length - 1].date) {
    result.push(data[data.length - 1])
  }

  return result
}

const renderResultChart = (result: CalculationResult) => {
  if (resultChartInstance) resultChartInstance.destroy()
  const ctx = resultChart.value?.getContext('2d')
  if (ctx) {
    const maxPoints = Math.max(Math.floor(ctx.canvas.width / 15), 26)
    const asapData = applyASAP(result.xirrs, maxPoints)

    resultChartInstance = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: asapData.map(x => x.date),
        datasets: [
          {
            label: 'XIRR',
            data: asapData.map(x => x.amount),
            backgroundColor: 'rgba(75, 192, 192, 0.6)',
            borderColor: 'rgba(75, 192, 192, 1)',
            borderWidth: 1,
          },
        ],
      },
      options: {
        responsive: true,
        scales: {
          x: {
            title: { display: true, text: 'Date' },
            grid: { display: false },
            ticks: {
              maxTicksLimit: 10,
              callback: function (val, index) {
                return index % Math.ceil(asapData.length / 10) === 0
                  ? this.getLabelForValue(val as number)
                  : ''
              },
            },
          },
          y: {
            title: { display: true, text: 'XIRR (%)' },
            ticks: {
              callback: value => (value as number).toFixed(2) + '%',
              maxTicksLimit: 8,
            },
          },
        },
        plugins: {
          title: { display: true, text: 'XIRR Rolling Result (ASAP)', font: { size: 16 } },
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: context => `XIRR: ${context.parsed.y.toFixed(2)}%`,
            },
          },
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

onMounted(() => {
  loadFromLocalStorage()
  calculate()
})

let debounceTimer: number | null = null
watch(
  form,
  () => {
    if (isUpdatingForm.value) return

    if (debounceTimer !== null) {
      clearTimeout(debounceTimer)
    }

    debounceTimer = setTimeout(() => {
      saveToLocalStorage()
      calculate()
      debounceTimer = null
    }, 1000) as unknown as number
  },
  { deep: true }
)
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
