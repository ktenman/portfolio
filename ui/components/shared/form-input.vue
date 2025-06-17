<template>
  <div :class="containerClass">
    <label v-if="label" :for="inputId" :class="labelClass">{{ label }}</label>
    <select
      v-if="type === 'select'"
      :id="inputId"
      v-model="model"
      :class="selectClass"
      v-bind="$attrs"
    >
      <option v-if="placeholder" value="">{{ placeholder }}</option>
      <option v-for="opt in options" :key="opt.value" :value="opt.value">
        {{ opt.text }}
      </option>
    </select>
    <input
      v-else
      :id="inputId"
      v-model="model"
      :type="type"
      :class="inputClass"
      v-bind="$attrs"
    />
    <div v-if="error" :class="errorClass">{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { FEATURE_FLAGS } from '../../config/features'

interface SelectOption {
  value: string | number
  text: string
}

interface Props {
  label?: string
  type?: 'text' | 'number' | 'date' | 'select'
  error?: string
  placeholder?: string
  options?: SelectOption[]
  step?: string | number
  min?: string | number
  max?: string | number
}

const props = withDefaults(defineProps<Props>(), {
  type: 'text',
})

const model = defineModel<string | number>()
const inputId = computed(() => `input-${Math.random().toString(36).slice(2, 9)}`)

const useTailwind = FEATURE_FLAGS.tailwindForms

// Container classes
const containerClass = computed(() => {
  return useTailwind ? 'mb-3' : 'mb-3'
})

// Label classes
const labelClass = computed(() => {
  if (useTailwind) {
    return 'block text-sm font-medium text-gray-700 mb-1'
  }
  return 'form-label'
})

// Input classes
const inputClass = computed(() => {
  const baseClasses = useTailwind
    ? 'block w-full px-3 py-2 border rounded-md shadow-sm sm:text-sm'
    : 'form-control'
  
  const validClasses = useTailwind
    ? 'border-gray-300 focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 focus:outline-none'
    : ''
  
  const invalidClasses = useTailwind
    ? 'border-red-300 text-red-900 placeholder-red-300 focus:border-red-500 focus:ring-red-500'
    : 'is-invalid'
  
  if (props.error) {
    return useTailwind ? `${baseClasses} ${invalidClasses}` : `${baseClasses} ${invalidClasses}`
  }
  
  return useTailwind ? `${baseClasses} ${validClasses}` : baseClasses
})

// Select classes (same as input for consistency)
const selectClass = computed(() => inputClass.value)

// Error message classes
const errorClass = computed(() => {
  if (useTailwind) {
    return 'mt-1 text-sm text-red-600'
  }
  return 'invalid-feedback'
})
</script>
