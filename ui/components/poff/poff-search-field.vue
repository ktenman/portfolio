<template>
  <div class="flex items-center gap-3" :class="{ 'md-down:w-full': !folded }">
    <button
      v-if="folded"
      type="button"
      class="-mr-2 inline-flex size-11 items-center justify-center text-ink-soft hover:text-ink md:hidden"
      :aria-label="LABEL"
      data-testid="poff-search-open"
      @click="start"
    >
      <radio-icon name="search" />
    </button>
    <label
      class="group relative z-0 flex min-w-0 flex-1 items-center before:absolute before:inset-x-0 before:top-1/2 before:-z-10 before:h-11 before:-translate-y-1/2"
      :class="{ 'md-down:hidden': folded }"
    >
      <radio-icon
        name="search"
        class="pointer-events-none absolute left-2.75 size-[15px] group-focus-within:text-brass"
        :class="query ? 'text-brass' : 'text-ink-faint'"
      />
      <span class="sr-only">{{ LABEL }}</span>
      <input
        ref="input"
        type="search"
        v-model="query"
        :placeholder="LABEL"
        enterkeyhint="search"
        autocomplete="off"
        class="h-9 min-w-0 flex-1 rounded-container border bg-surface-subtle pr-11 pl-8.5 text-sm placeholder:text-ink-soft focus:bg-surface [&::-webkit-search-cancel-button]:hidden"
        :class="query ? 'border-brass' : 'border-control-border'"
        data-testid="poff-search"
      />
      <button
        v-if="query"
        type="button"
        class="absolute right-0 inline-flex size-11 items-center justify-center text-ink-soft hover:text-ink"
        aria-label="Clear the search"
        @click="close"
      >
        <radio-icon name="close" class="size-4" />
      </button>
    </label>
    <button
      v-if="query !== null"
      type="button"
      class="min-h-11 font-medium text-ink md:hidden"
      @click="close"
    >
      Cancel
    </button>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, useTemplateRef } from 'vue'
import { onKeyStroke } from '@vueuse/core'
import { usePoffFilters } from '../../composables/use-poff-filters'
import RadioIcon from '../radio/radio-icon.vue'

const props = defineProps<{ compact?: boolean }>()

const LABEL = 'Search films, people, cinemas'

const { query } = usePoffFilters()
const input = useTemplateRef<HTMLInputElement>('input')
const folded = computed(() => props.compact && query.value === null)

const start = async () => {
  query.value = ''
  await nextTick()
  input.value?.focus()
}

const close = () => {
  query.value = null
  input.value?.blur()
}

onKeyStroke('/', event => {
  const target = event.target as Element
  if (target.closest?.('input, textarea, select, [contenteditable]')) return
  event.preventDefault()
  input.value?.focus()
})

onKeyStroke('Escape', event => {
  if (query.value !== null && !(event.target as Element).closest?.('dialog')) close()
})
</script>
