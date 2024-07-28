<template>
  <div class="container mt-2">
    <h4 class="mb-4">Investment Calculator</h4>
    <div class="row">
      <div class="col-md-4">
        <form @submit.prevent="calculate">
          <div class="mb-3">
            <label for="initialWorth" class="form-label">Initial Worth (€):</label>
            <input
              type="number"
              v-model="form.initialWorth"
              id="initialWorth"
              class="form-control"
              step="0.01"
              required
            />
          </div>
          <div class="mb-3">
            <label for="monthlyInvestment" class="form-label">Monthly Investment (€):</label>
            <input
              type="number"
              v-model="form.monthlyInvestment"
              id="monthlyInvestment"
              class="form-control"
              step="0.01"
              required
            />
          </div>
          <div class="mb-3">
            <label for="yearlyGrowthRate" class="form-label">Yearly Growth Rate (%):</label>
            <input
              type="number"
              v-model="form.yearlyGrowthRate"
              id="yearlyGrowthRate"
              class="form-control"
              step="0.001"
              required
            />
          </div>
          <div class="mb-3">
            <label for="annualReturnRate" class="form-label">Annual Return Rate (%):</label>
            <input
              type="number"
              v-model="form.annualReturnRate"
              id="annualReturnRate"
              class="form-control"
              step="0.001"
              required
            />
          </div>
          <div class="mb-3">
            <label for="years" class="form-label">Number of Years:</label>
            <input
              type="number"
              v-model="form.years"
              id="years"
              class="form-control"
              step="1"
              min="1"
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
      const initialWorth = Math.max(0, form.initialWorth)
      let monthlyInvestment = Math.max(0, form.monthlyInvestment)
      const yearlyGrowthRate = Math.max(0, form.yearlyGrowthRate)
      const annualReturnRate = Math.max(0, form.annualReturnRate)
      const years = Math.max(1, Math.floor(form.years))

      const values = []
      let totalWorth = initialWorth
      yearSummary.value = []

      for (let year = 1; year <= years; year++) {
        let yearStartWorth = totalWorth
        for (let month = 1; month <= 12; month++) {
          totalWorth += monthlyInvestment
          totalWorth += totalWorth * (annualReturnRate / 100 / 12)
        }
        monthlyInvestment *= 1 + yearlyGrowthRate / 100
        values.push(totalWorth)

        const yearGrowth = totalWorth - yearStartWorth
        const earningsPerDay = (totalWorth * (annualReturnRate / 100)) / 365.25

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

    const formatCurrency = value => {
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'EUR',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(value)
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
