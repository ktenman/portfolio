<template>
  <div class="container mt-4">
    <h2 class="mb-4">Portfolio Chart Migration Test</h2>
    
    <div class="alert alert-info mb-4">
      <p class="mb-2">
        <strong>Feature Flag Status:</strong> 
        <span :class="FEATURE_FLAGS.tailwindCharts ? 'text-success' : 'text-danger'">
          {{ FEATURE_FLAGS.tailwindCharts ? 'Tailwind Enabled' : 'Bootstrap/Current Version' }}
        </span>
      </p>
      <p class="mb-0">
        The PortfolioChart component has minimal Bootstrap usage - only a margin class (mb-3) and custom SCSS.
      </p>
    </div>

    <div class="row">
      <div class="col-12">
        <h3>Current PortfolioChart Component</h3>
        <div class="card">
          <div class="card-body">
            <portfolio-chart :data="mockChartData" />
          </div>
        </div>
      </div>
    </div>

    <div class="row mt-4">
      <div class="col-md-6">
        <h4>Current Styles (SCSS)</h4>
        <div class="bg-light p-3 rounded">
          <pre><code>.chart-container {
  margin-bottom: 1rem; /* mb-3 */
  
  @media (min-width: 992px) {
    height: 25rem;
  }
}</code></pre>
        </div>
      </div>
      <div class="col-md-6">
        <h4>Tailwind Equivalent</h4>
        <div class="bg-light p-3 rounded">
          <pre><code>class="mb-3 lg:h-[25rem]"

/* Or with custom height */
class="mb-3 lg:h-96"</code></pre>
        </div>
      </div>
    </div>

    <div class="mt-4">
      <h3>Migration Notes</h3>
      <ul>
        <li>Bootstrap class: <code>mb-3</code> → Tailwind: <code>mb-3</code> (identical)</li>
        <li>Custom SCSS media query → Tailwind: <code>lg:h-[25rem]</code> or <code>lg:h-96</code></li>
        <li>The chart itself uses Chart.js and doesn't depend on Bootstrap</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import PortfolioChart from '../portfolio/portfolio-chart.vue'
import { FEATURE_FLAGS } from '../../config/features'

// Generate mock data for testing
const generateMockData = () => {
  const labels = []
  const totalValues = []
  const profitValues = []
  const xirrValues = []
  const earningsValues = []
  
  // Generate 12 months of data
  for (let i = 11; i >= 0; i--) {
    const date = new Date()
    date.setMonth(date.getMonth() - i)
    labels.push(date.toISOString().split('T')[0])
    
    // Generate realistic-looking data
    const baseValue = 10000 + (11 - i) * 1000 + Math.random() * 2000
    totalValues.push(baseValue)
    profitValues.push(baseValue * 0.1 + Math.random() * 500)
    xirrValues.push(8 + Math.random() * 4)
    earningsValues.push(100 + Math.random() * 50)
  }
  
  return {
    labels,
    totalValues,
    profitValues,
    xirrValues,
    earningsValues,
  }
}

const mockChartData = ref(generateMockData())
</script>