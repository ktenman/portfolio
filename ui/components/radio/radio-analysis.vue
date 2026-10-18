<template>
  <div class="grid gap-4">
    <radio-card title="Teemad">
      <template #aside>
        <span class="text-sm text-ink-soft">Masinanalüüs</span>
      </template>
      <p v-if="!topics.length" class="mb-0 text-ink-soft">Teemasid veel pole.</p>
      <ul v-else class="mb-0 list-none p-0">
        <li
          v-for="topic in topics"
          :key="topic.start"
          class="grid grid-cols-[3.5rem_minmax(0,1fr)_auto] items-baseline gap-3 border-b border-hairline py-2.5 last:border-b-0"
          data-testid="radio-topic"
        >
          <button
            type="button"
            class="text-left text-ink-soft tabular-nums hover:text-ink"
            :aria-label="`Kuula: ${topic.title}`"
            @click="player.playFrom(topic.start)"
          >
            {{ clock(recording.status.startedAt, topic.start) }}
          </button>
          <span class="font-semibold">{{ topic.title }}</span>
          <span class="text-sm text-ink-soft tabular-nums">
            {{ minutes(topic.end - topic.start) }}
          </span>
        </li>
      </ul>
    </radio-card>
    <radio-card title="Reklaam" data-testid="radio-ads">
      <template #aside>
        <radio-badge tone="plain">{{ breaks.length ? 'Leitud' : 'Ei leitud' }}</radio-badge>
      </template>
      <radio-facts v-if="breaks.length" :rows="breaks" />
      <p v-else class="mb-0 text-sm text-ink-soft">Masin ei leidnud saatest reklaamipausi.</p>
    </radio-card>
    <radio-card title="Tuvastus" data-testid="radio-detection">
      <radio-facts v-if="detection.length" :rows="detection" />
      <p v-else class="mb-0 text-sm text-ink-soft">Saadet pole veel analüüsitud.</p>
    </radio-card>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { CutKind, type RecordingSummary } from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { clock, length, minutes } from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const player = useRadioPlayer()

const topics = computed(() => props.recording.timeline.filter(entry => !entry.cut))

const breaks = computed<[string, string][]>(() =>
  props.recording.cuts
    .filter(cut => cut.kind === CutKind.BREAK)
    .map(cut => [
      `${clock(props.recording.status.startedAt, cut.start)}–${clock(props.recording.status.startedAt, cut.end)}`,
      length(cut.end - cut.start),
    ])
)

const detection = computed<[string, string][]>(() => {
  const edit = props.recording.editList
  if (!edit) return []
  return [
    ['Mudel', edit.outline.model ?? '—'],
    ['Lugemisi', `${edit.outline.matched} / ${edit.outline.reads}`],
    ['Saatejuhid', edit.hosts.join(', ') || '—'],
  ]
})
</script>
