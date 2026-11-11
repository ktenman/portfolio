<template>
  <template v-for="(part, i) in highlight(text, query)" :key="i">
    <mark v-if="part.mark" class="rounded-[2px] bg-brass-wash text-inherit">{{ part.text }}</mark>
    <template v-else>{{ part.text }}</template>
  </template>
</template>

<script lang="ts" setup>
import { highlight } from '../../utils/poff-search'

defineProps<{ text: string; query: string }>()
</script>
