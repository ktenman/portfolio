<template>
  <div class="container mt-2">
    <h4 class="mb-4">Investment Calculator</h4>
    <div class="row">
      <div class="col-md-4">
        <form @submit.prevent="calculate">
          <div class="mb-3" v-for="(field, key) in form" :key="key">
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
          <button type="submit" class="btn btn-primary">Recalculate</button>
        </form>
      </div>
      <div class="col-md-8">
        <canvas ref="portfolioChart"></canvas>
      </div>
    </div>
    <div class="row mt-4">
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

<script>
import { ref, reactive, onMounted, watch } from 'vue'
import Chart from 'chart.js/auto'

export default {
  setup() {
    const form = reactive({
      initialWorth: 1000,
      monthlyInvestment: 2800,
      yearlyGrowthRate: 5,
      annualReturnRate: 25.341,
      years: 10,
    })

    const portfolioChart = ref(null)
    const yearSummary = ref([])
    let chart = null

    const calculate = () => {
      const { initialWorth, monthlyInvestment, yearlyGrowthRate, annualReturnRate, years } = form
      const values = []
      let totalWorth = Math.max(0, parseFloat(initialWorth))
      let currentMonthlyInvestment = Math.max(0, parseFloat(monthlyInvestment))
      const growthRate = Math.max(0, parseFloat(yearlyGrowthRate)) / 100
      const returnRate = Math.max(0, parseFloat(annualReturnRate)) / 100
      const numYears = Math.max(1, parseInt(years))

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

      renderChart(values)
    }

    const renderChart = data => {
      if (chart) {
        chart.destroy()
      }

      if (portfolioChart.value) {
        const ctx = portfolioChart.value.getContext('2d')
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

    const formatCurrency = value => {
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'EUR',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(value)
    }

    const getFieldLabel = key => {
      const labels = {
        initialWorth: 'Initial Worth (€)',
        monthlyInvestment: 'Monthly Investment (€)',
        yearlyGrowthRate: 'Yearly Growth Rate (%)',
        annualReturnRate: 'Annual Return Rate (%)',
        years: 'Number of Years',
      }
      return labels[key] || key
    }

    const getFieldStep = key => {
      const steps = {
        initialWorth: '0.01',
        monthlyInvestment: '0.01',
        yearlyGrowthRate: '0.001',
        annualReturnRate: '0.001',
        years: '1',
      }
      return steps[key] || 'any'
    }

    onMounted(() => {
      calculate()
    })

    watch(
      form,
      () => {
        calculate()
      },
      { deep: true }
    )

    return {
      form,
      calculate,
      portfolioChart,
      yearSummary,
      formatCurrency,
      getFieldLabel,
      getFieldStep,
    }
  },
}
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
