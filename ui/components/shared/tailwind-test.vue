<template>
  <div class="container mt-4">
    <h2 class="mb-4">Tailwind CSS v4.1 Test Component</h2>

    <div class="row mb-4">
      <div class="col-md-6">
        <h3>Bootstrap Button</h3>
        <button class="btn btn-primary">Bootstrap Primary</button>
      </div>
      <div class="col-md-6">
        <h3>Tailwind Button</h3>
        <button
          class="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-colors"
        >
          Tailwind Primary
        </button>
      </div>
    </div>

    <div class="row mb-4">
      <div class="col-md-6">
        <h3>Bootstrap Alert</h3>
        <div class="alert alert-info">This is a Bootstrap info alert</div>
      </div>
      <div class="col-md-6">
        <h3>Tailwind Alert</h3>
        <div class="bg-blue-50 border-l-4 border-blue-400 text-blue-700 p-4 rounded">
          This is a Tailwind info alert
        </div>
      </div>
    </div>

    <div class="row">
      <div class="col-md-6">
        <h3>LoadingSpinner Component (Current)</h3>
        <loading-spinner />
        <loading-spinner size="sm" class="mt-2" />
        <loading-spinner size="lg" show-message class="mt-2" />
      </div>
      <div class="col-md-6">
        <h3>Tailwind Spinner (Direct)</h3>
        <div class="space-y-2">
          <div>
            <span class="text-sm text-gray-600">Default (md):</span>
            <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 ml-2"></div>
          </div>
          <div>
            <span class="text-sm text-gray-600">Small:</span>
            <div class="inline-block animate-spin rounded-full h-4 w-4 border-b-2 border-blue-600 ml-2"></div>
          </div>
          <div>
            <span class="text-sm text-gray-600">Large with message:</span>
            <div class="inline-flex items-center">
              <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
              <span class="ml-2">Loading...</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="mt-4 p-4 bg-gray-100 rounded">
      <h3 class="text-lg font-semibold mb-2">Feature Flags Status</h3>
      <ul class="list-disc pl-5">
        <li v-for="(enabled, feature) in featureFlags" :key="feature">
          {{ feature }}:
          <span :class="enabled ? 'text-green-600' : 'text-red-600'" class="font-semibold">
            {{ enabled ? 'Enabled' : 'Disabled' }}
          </span>
        </li>
      </ul>
    </div>
    
    <div class="mt-4 p-4 border-2 border-dashed border-gray-300 rounded-lg">
      <h3 class="text-xl font-bold mb-4">Tailwind CSS v4 Test Cases</h3>
      <div class="space-y-4">
        <div>
          <p class="font-medium mb-2">Colors Test:</p>
          <div class="flex gap-2">
            <div class="w-10 h-10 bg-red-500 rounded"></div>
            <div class="w-10 h-10 bg-blue-500 rounded"></div>
            <div class="w-10 h-10 bg-green-500 rounded"></div>
            <div class="w-10 h-10 bg-yellow-500 rounded"></div>
          </div>
        </div>
        <div>
          <p class="font-medium mb-2">Typography Test:</p>
          <p class="text-xs">Extra Small Text</p>
          <p class="text-sm">Small Text</p>
          <p class="text-base">Base Text</p>
          <p class="text-lg">Large Text</p>
          <p class="text-xl">Extra Large Text</p>
        </div>
        <div>
          <p class="font-medium mb-2">Flexbox Test:</p>
          <div class="flex items-center justify-between bg-gray-200 p-2 rounded">
            <span>Left</span>
            <span>Center</span>
            <span>Right</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { FEATURE_FLAGS } from '../../config/features'
import LoadingSpinner from './loading-spinner.vue'

const featureFlags = FEATURE_FLAGS
</script>
