<template>
  <div class="mb-3">
    <label v-if="label" :for="inputId" class="form-label">{{ label }}</label>
    <select
      v-if="type === 'select'"
      :id="inputId"
      v-model="model"
      class="form-select"
      :class="{ 'is-invalid': error }"
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
      class="form-control"
      :class="{ 'is-invalid': error }"
      v-bind="$attrs"
    />
    <div v-if="error" class="invalid-feedback">{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

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
}

withDefaults(defineProps<Props>(), {
  type: 'text',
})

const model = defineModel<string | number>()
const inputId = computed(() => `input-${Math.random().toString(36).slice(2, 9)}`)
</script>
