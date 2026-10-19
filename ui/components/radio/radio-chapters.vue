<template>
  <radio-card title="Peatükid">
    <p v-if="!chapters.length" class="mb-0 text-ink-soft">Peatükke pole.</p>
    <ol v-else class="mb-0 list-none p-0">
      <li
        v-for="(chapter, i) in chapters"
        :key="chapter.start"
        class="border-b border-hairline last:border-b-0"
      >
        <button
          type="button"
          data-testid="radio-chapter"
          :aria-current="i === current ? 'true' : undefined"
          class="flex min-h-11 w-full cursor-pointer items-baseline gap-4 px-1 py-2 text-left transition-colors hover:bg-surface-hover aria-[current=true]:font-semibold"
          @click="player.playFrom(chapter.start, 'episode')"
        >
          <span class="w-14 shrink-0 font-normal text-ink-soft tabular-nums">
            {{ position(chapter.start) }}
          </span>
          <span class="min-w-0 grow">{{ chapter.title }}</span>
          <radio-badge v-if="i === current" tone="live">Praegu</radio-badge>
        </button>
      </li>
    </ol>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { RecordingSummary } from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { position } from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const player = useRadioPlayer()

const chapters = computed(() => props.recording.editList?.chapters ?? [])
const current = computed(() =>
  player.recording.value?.name === props.recording.name && player.version.value === 'episode'
    ? chapters.value.filter(chapter => chapter.start <= player.seconds.value).length - 1
    : -1
)
</script>
