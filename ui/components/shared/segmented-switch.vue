<template>
  <div>
    <div class="flex" role="group" :aria-label="label">
      <button
        v-for="option in options"
        :key="option.value"
        class="relative border border-hairline bg-surface-sunken px-2.75 py-1.25 text-label font-medium text-ink-soft transition-colors first:rounded-l-container last:-ml-px last:rounded-r-container hover:not-aria-pressed:bg-surface-hover hover:not-aria-pressed:text-ink focus-visible:z-2 aria-pressed:z-1 aria-pressed:border-brass aria-pressed:bg-brass-wash aria-pressed:text-brass-deep max-sm:flex-1 md-down:before:absolute md-down:before:inset-x-0 md-down:before:top-1/2 md-down:before:h-11 md-down:before:-translate-y-1/2"
        :class="{
          'inline-flex items-center justify-center gap-1.5 max-md:size-11 max-md:flex-none max-md:p-0':
            option.icon,
        }"
        :aria-pressed="option.value === selected"
        type="button"
        @click="emit('select', option.value)"
      >
        <template v-if="option.icon">
          <svg
            aria-hidden="true"
            class="size-4 shrink-0"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path :d="option.icon" />
          </svg>
          <span class="max-md:sr-only">{{ option.label }}</span>
        </template>
        <template v-else>{{ option.label }}</template>
      </button>
    </div>
    <p v-if="announcement !== undefined" class="sr-only" role="status">{{ announcement }}</p>
  </div>
</template>

<script setup lang="ts" generic="T extends string">
defineProps<{
  label: string
  options: { value: T; label: string; icon?: string }[]
  selected: T
  announcement?: string
}>()

const emit = defineEmits<{
  select: [value: T]
}>()
</script>
