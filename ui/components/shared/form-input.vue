<template>
  <div class="mb-4">
    <label v-if="label" :for="inputId" class="form-label">{{ label }}</label>
    <select
      v-if="type === 'select'"
      :id="inputId"
      v-model="selectModel"
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
      :placeholder="placeholder"
      class="form-control"
      :class="{ 'is-invalid': error }"
      v-bind="$attrs"
      @wheel="blurOnWheel"
    />
    <div v-if="error" class="invalid-feedback">{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { blurOnWheel } from '../../utils/dom'

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
  id?: string
}

const props = withDefaults(defineProps<Props>(), {
  type: 'text',
})

const model = defineModel<string | number>()
const selectModel = computed({
  get: () => model.value ?? '',
  set: value => {
    model.value = value
  },
})
const inputId = computed(() => props.id || `input-${Math.random().toString(36).slice(2, 9)}`)
</script>
