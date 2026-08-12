<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3">
    <div class="mb-6 flex items-start justify-between">
      <div class="grow">
        <div class="flex items-center justify-between">
          <h2 class="mb-0" @click="$emit('title-click')">{{ title }}</h2>
          <div class="flex items-center gap-4">
            <slot name="toolbar" />
            <button
              v-if="showAddButton"
              :id="addButtonId"
              class="btn btn-primary btn-add-new hidden md:block"
              @click="$emit('add')"
            >
              {{ addButtonText }}
            </button>
          </div>
        </div>
        <div class="flex items-center justify-between">
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
