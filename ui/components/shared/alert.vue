<template>
  <div v-if="visible" class="mt-3">
    <div 
      :class="['alert', `alert-${type}`, 'alert-dismissible']"
      role="alert"
    >
      <strong v-if="message">{{ message }}</strong>
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

type AlertType = 'success' | 'danger' | 'warning' | 'info'

interface Props {
  type?: AlertType
  message?: string
  dismissible?: boolean
  duration?: number
  modelValue?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  type: 'info',
  message: '',
  dismissible: true,
  duration: 0,
  modelValue: true
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  dismiss: []
}>()

const visible = ref(props.modelValue)

watch(() => props.modelValue, (newValue) => {
  visible.value = newValue
})

watch(visible, (newValue) => {
  if (newValue && props.duration > 0) {
    setTimeout(() => {
      visible.value = false
      emit('update:modelValue', false)
      emit('dismiss')
    }, props.duration)
  }
})
</script>

<style scoped>
/* Additional styles if necessary - Bootstrap handles most styling */
</style>