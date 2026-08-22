<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3">
    <div class="mb-6 flex items-start justify-between">
      <div class="grow">
        <div class="flex flex-wrap items-center justify-between gap-y-2">
          <div class="flex items-center gap-3">
            <h2 class="mb-0" @click="$emit('title-click')">{{ title }}</h2>
            <slot name="title-suffix" />
          </div>
          <div class="flex items-center gap-4">
            <slot name="toolbar" />
            <button
              v-if="showAddButton"
              :id="addButtonId"
              class="btn btn-primary btn-add-new hidden cursor-pointer rounded-container border border-control-border bg-[rgb(0_0_0/0.02)] px-3 py-1 text-center align-middle text-sm leading-normal font-normal text-gray-700 shadow-control transition-all duration-200 select-none before:mr-2 before:text-[1.1em] before:font-light before:opacity-70 before:transition-opacity before:duration-200 before:content-['+'] hover:bg-brass-wash hover:text-brass-deep hover:before:opacity-100 active:bg-brass-wash active:shadow-[inset_0_1px_1px_rgb(0_0_0/0.04)] md-down:min-h-11 md:block"
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
