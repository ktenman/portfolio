<template>
  <div class="container mt-3">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <h2 class="mb-0">{{ title }}</h2>
      <button
        v-if="showAddButton"
        :id="addButtonId"
        class="btn btn-primary btn-add-new"
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
