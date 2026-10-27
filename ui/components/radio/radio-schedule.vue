<template>
  <section>
    <div class="mb-3 flex flex-wrap items-baseline justify-between gap-2">
      <h3 class="mb-0 text-heading">Schedule</h3>
      <span class="text-sm text-ink-soft">
        {{ fullDay(day.from) }}
        <span v-if="summary" class="md:hidden" data-testid="radio-schedule-summary">
          · {{ summary }}
        </span>
      </span>
    </div>
    <p v-if="!day.day.length && !day.night.length" class="text-ink-soft">
      The schedule for this day has not been loaded yet.
    </p>
    <div
      v-else
      class="divide-y divide-hairline overflow-hidden rounded-container border border-hairline bg-surface"
    >
      <template v-for="section in sections" :key="section.key">
        <p
          v-if="section.divider"
          class="mb-0 bg-surface-sunken px-3 py-2 text-sm text-ink-soft md:px-4"
        >
          {{ section.divider }}
        </p>
        <template v-for="block in section.blocks" :key="block.airings[0].id">
          <button
            v-if="block.missed"
            type="button"
            class="flex w-full cursor-pointer items-center gap-2 bg-surface-sunken px-3 py-2.5 text-left text-sm text-ink-soft hover:text-ink md:px-4"
            :aria-expanded="open.has(block.airings[0].id)"
            @click="toggle(block.airings[0].id)"
          >
            <radio-icon
              name="down"
              class="transition-transform"
              :class="{ 'rotate-180': open.has(block.airings[0].id) }"
            />
            {{ missedRun(block.airings) }}
          </button>
          <template v-if="!block.missed || open.has(block.airings[0].id)">
            <radio-airing-row v-for="a in block.airings" :key="a.id" :airing="a" />
          </template>
        </template>
      </template>
    </div>
  </section>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import type { RadioDay } from '../../models/generated/domain-models'
import { fullDay, missedRun, nightOf, recordedSummary } from '../../utils/radio'
import RadioAiringRow from './radio-airing-row.vue'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ day: RadioDay }>()

const open = ref(new Set<number>())

const summary = computed(() => recordedSummary(props.day))

const sections = computed(() => [
  { key: 'day', divider: null, blocks: props.day.day },
  {
    key: 'night',
    divider: props.day.night.length ? nightOf(props.day.until) : null,
    blocks: props.day.night,
  },
])

const toggle = (id: number) => {
  if (!open.value.delete(id)) open.value.add(id)
}
</script>
