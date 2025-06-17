<template>
  <div class="container mt-4">
    <h2 class="mb-4">Form Input Migration Test</h2>
    
    <div class="alert" :class="FEATURE_FLAGS.tailwindForms ? 'alert-success' : 'alert-info'">
      <p class="mb-0">
        <strong>Feature Flag Status:</strong> 
        {{ FEATURE_FLAGS.tailwindForms ? 'Tailwind Enabled ✓' : 'Bootstrap Version (Current)' }}
      </p>
    </div>

    <div class="row mt-4">
      <div class="col-md-6">
        <h3>Text Input</h3>
        <form-input
          v-model="textValue"
          label="Full Name"
          placeholder="Enter your name"
          :error="textError"
        />
        <button class="btn btn-sm btn-primary" @click="toggleTextError">
          Toggle Error State
        </button>
      </div>
      
      <div class="col-md-6">
        <h3>Select Input</h3>
        <form-input
          v-model="selectValue"
          type="select"
          label="Choose Currency"
          placeholder="Select a currency"
          :options="currencyOptions"
          :error="selectError"
        />
        <button class="btn btn-sm btn-primary" @click="toggleSelectError">
          Toggle Error State
        </button>
      </div>
    </div>

    <div class="row mt-4">
      <div class="col-md-6">
        <h3>Number Input</h3>
        <form-input
          v-model="numberValue"
          type="number"
          label="Amount"
          placeholder="0.00"
          step="0.01"
          min="0"
          :error="numberError"
        />
      </div>
      
      <div class="col-md-6">
        <h3>Date Input</h3>
        <form-input
          v-model="dateValue"
          type="date"
          label="Transaction Date"
        />
      </div>
    </div>

    <div class="row mt-4">
      <div class="col-12">
        <h3>Bootstrap → Tailwind Class Mapping</h3>
        <div class="table-responsive">
          <table class="table table-sm">
            <thead>
              <tr>
                <th>Element</th>
                <th>Bootstrap Classes</th>
                <th>Tailwind Equivalent</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Container</td>
                <td><code>mb-3</code></td>
                <td><code>mb-3</code></td>
              </tr>
              <tr>
                <td>Label</td>
                <td><code>form-label</code></td>
                <td><code>block text-sm font-medium text-gray-700 mb-1</code></td>
              </tr>
              <tr>
                <td>Input</td>
                <td><code>form-control</code></td>
                <td><code>block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500</code></td>
              </tr>
              <tr>
                <td>Select</td>
                <td><code>form-select</code></td>
                <td><code>block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500</code></td>
              </tr>
              <tr>
                <td>Invalid State</td>
                <td><code>is-invalid</code></td>
                <td><code>border-red-300 text-red-900 placeholder-red-300 focus:border-red-500 focus:ring-red-500</code></td>
              </tr>
              <tr>
                <td>Error Message</td>
                <td><code>invalid-feedback</code></td>
                <td><code>mt-1 text-sm text-red-600</code></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mt-4">
      <h3>Form Values</h3>
      <pre class="bg-light p-3 rounded">{{ formValues }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import FormInput from '../shared/form-input.vue'
import { FEATURE_FLAGS } from '../../config/features'

// Form values
const textValue = ref('')
const selectValue = ref('')
const numberValue = ref<number | string>('')
const dateValue = ref('')

// Error states
const textError = ref('')
const selectError = ref('')
const numberError = ref('')

// Options for select
const currencyOptions = [
  { value: 'EUR', text: 'Euro (EUR)' },
  { value: 'USD', text: 'US Dollar (USD)' },
  { value: 'GBP', text: 'British Pound (GBP)' },
  { value: 'JPY', text: 'Japanese Yen (JPY)' },
]

// Toggle error states for testing
const toggleTextError = () => {
  textError.value = textError.value ? '' : 'Please enter a valid name'
}

const toggleSelectError = () => {
  selectError.value = selectError.value ? '' : 'Please select a currency'
}

// Computed values display
const formValues = computed(() => ({
  text: textValue.value,
  select: selectValue.value,
  number: numberValue.value,
  date: dateValue.value,
}))
</script>