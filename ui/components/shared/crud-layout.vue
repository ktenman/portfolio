<template>
  <div class="tw:mx-auto tw:mt-4 tw:w-full tw:max-w-[min(1350px,91vw)] tw:px-3">
    <div class="tw:mb-6 tw:flex tw:items-start tw:justify-between">
      <div class="tw:grow">
        <div class="tw:flex tw:items-center tw:justify-between">
          <h2 class="tw:mb-0" @click="$emit('title-click')">{{ title }}</h2>
          <div class="tw:flex tw:items-center tw:gap-4">
            <slot name="toolbar" />
            <button
              v-if="showAddButton"
              :id="addButtonId"
              class="btn btn-primary btn-add-new tw:hidden tw:md:block"
              @click="$emit('add')"
            >
              {{ addButtonText }}
            </button>
          </div>
        </div>
        <div class="tw:flex tw:items-center tw:justify-between">
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
