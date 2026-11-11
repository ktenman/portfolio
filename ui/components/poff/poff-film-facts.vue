<template>
  <div class="flex flex-col gap-8">
    <section v-for="section in sections" :key="section.title">
      <h3 :class="HEADING">{{ section.title }}</h3>
      <dl v-if="section.rows.length" :class="TABLE">
        <div v-for="[label, value] in section.rows" :key="label" :class="ROW">
          <dt class="text-sm text-ink-soft">{{ label }}</dt>
          <dd>{{ value }}</dd>
        </div>
      </dl>
      <details v-if="section.more.length" :class="[TABLE, 'group mt-2']">
        <summary
          class="flex min-h-11 cursor-pointer list-none items-center justify-between px-3.5 font-medium [&::-webkit-details-marker]:hidden"
        >
          All credits
          <radio-icon name="down" class="size-4 group-open:rotate-180" />
        </summary>
        <dl class="m-0">
          <div v-for="[label, value] in section.more" :key="label" :class="[ROW, 'border-t']">
            <dt class="text-sm text-ink-soft">{{ label }}</dt>
            <dd>{{ value }}</dd>
          </div>
        </dl>
      </details>
    </section>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { PoffFilm } from '../../models/generated/domain-models'
import { HEADING } from '../../utils/poff'
import { credits, details } from '../../utils/poff-film'
import RadioIcon from '../radio/radio-icon.vue'

const props = defineProps<{ film: PoffFilm }>()

const TABLE = 'm-0 rounded-container border border-hairline bg-surface'
const ROW =
  'grid grid-cols-[8.5rem_minmax(0,1fr)] gap-3 border-hairline px-3.5 py-2.5 not-first:border-t [&_dd]:m-0'
const sections = computed(() => {
  const { main, rest } = credits(props.film)
  return [
    { title: 'Details', rows: details(props.film), more: [] },
    { title: 'Cast and crew', rows: main, more: rest },
  ].filter(s => s.rows.length || s.more.length)
})
</script>
