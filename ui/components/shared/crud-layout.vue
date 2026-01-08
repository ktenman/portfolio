<template>
  <div class="container mt-3">
    <div class="d-flex justify-content-between align-items-start mb-4">
      <div class="flex-grow-1">
        <div class="d-flex justify-content-between align-items-center">
          <h2 class="mb-0" @click="$emit('title-click')">{{ title }}</h2>
          <div class="d-flex align-items-center gap-3">
            <slot name="toolbar" />
            <button
              v-if="showAddButton"
              :id="addButtonId"
              class="btn btn-primary btn-add-new d-none d-md-block"
              @click="$emit('add')"
            >
              {{ addButtonText }}
            </button>
          </div>
        </div>
        <div class="d-flex justify-content-between align-items-center">
          <slot name="subtitle" />
          <slot name="subtitle-end" />
        </div>
      </div>
    </div>

    <slot name="content" />

    <slot name="modals" />
  </div>
</template>

<script setup lang="ts">
interface Props {
  title: string
  addButtonText?: string
  addButtonId?: string
  showAddButton?: boolean
}

withDefaults(defineProps<Props>(), {
  showAddButton: true,
  addButtonId: 'addNewItem',
  addButtonText: '',
})

defineEmits<{
  add: []
  'title-click': []
}>()
</script>
