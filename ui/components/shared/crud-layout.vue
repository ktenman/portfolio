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
        {{ addButtonText }}
      </button>
    </div>

    <slot name="content" />

    <slot name="modals" />
  </div>
</template>

<script setup lang="ts">
interface Props {
  title: string
  addButtonText: string
  addButtonId?: string
  showAddButton?: boolean
}

withDefaults(defineProps<Props>(), {
  showAddButton: true,
  addButtonId: 'addNewItem',
})

defineEmits<{
  add: []
}>()
</script>
