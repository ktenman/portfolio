<template>
  <radio-card title="Tuvastus" data-testid="radio-detection">
    <radio-facts v-if="detection.length" :rows="detection" />
    <p v-else class="mb-0 text-sm text-ink-soft">Saadet pole veel analüüsitud.</p>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { RecordingSummary } from '../../models/generated/domain-models'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'

const props = defineProps<{ recording: RecordingSummary }>()

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
