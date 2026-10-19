<template>
  <div class="grid gap-4">
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
import { length, range } from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const breaks = computed<[string, string][]>(() =>
  props.recording.cuts
    .filter(cut => cut.kind === CutKind.BREAK)
    .map(cut => [
      range(props.recording.status.startedAt, cut.start, cut.end),
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
