<template>
  <section class="card mb-0">
    <div class="card-body" :class="{ 'py-1.5': fold }">
      <div
        class="flex flex-wrap justify-between gap-2"
        :class="fold ? 'items-center' : 'mb-3 items-baseline'"
      >
        <h3 class="mb-0 text-heading" :class="{ grow: fold }">
          <button
            v-if="fold"
            type="button"
            class="flex min-h-12 w-full cursor-pointer items-center gap-2.5 text-left focus-visible:outline-2 focus-visible:outline-brass-deep"
            :aria-expanded="open"
            @click="open = !open"
          >
            <radio-icon
              name="down"
              class="text-ink-soft motion-safe:transition-transform"
              :class="{ '-rotate-90': !open }"
            />
            {{ title }}
          </button>
          <template v-else>{{ title }}</template>
        </h3>
        <slot name="aside" :open="open" />
      </div>
      <div v-if="open" :class="{ 'pb-2.5': fold }">
        <slot />
      </div>
    </div>
  </section>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ title: string; fold?: 'open' | 'shut' }>()

const open = props.fold
  ? useLocalStorage(`radio-fold:${props.title}`, props.fold === 'open')
  : ref(true)
</script>
