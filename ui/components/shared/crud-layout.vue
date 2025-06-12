<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h4 class="mb-0">{{ title }}</h4>
      <button
        v-if="showAddButton"
        :id="addButtonId"
        class="btn btn-primary btn-sm"
        @click="$emit('add')"
      >
        <font-awesome-icon icon="plus" />
        <span class="d-none d-md-inline ms-1">{{ addButtonText }}</span>
      </button>
    </div>

    <slot name="content" />

    <slot name="modals" />

    <alert :model-value="showAlert" :type="alertType" :message="alertMessage" :duration="5000" @update:model-value="$emit('update:showAlert', $event)" />
  </div>
</template>

<script setup lang="ts">
import Alert from './alert.vue'
import { AlertType } from '../../constants/ui-constants'

interface Props {
  title: string
  addButtonText: string
  addButtonId?: string
  showAddButton?: boolean
  showAlert: boolean
  alertType: AlertType
  alertMessage: string
}

withDefaults(defineProps<Props>(), {
  showAddButton: true,
  addButtonId: 'addNewItem',
})

defineEmits<{
  add: []
  'update:showAlert': [value: boolean]
}>()
</script>
