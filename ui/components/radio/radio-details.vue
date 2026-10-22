<template>
  <radio-card title="Processing" fold="shut" data-testid="radio-details">
    <template v-if="recording.episodeBytes" #aside>
      <a
        :href="url('episode.m4a')"
        download
        class="-mr-2 inline-flex min-h-11 items-center gap-1.5 rounded-full px-3 text-sm font-medium whitespace-nowrap text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep"
        :aria-label="`Download the clean version, ${megabytes(recording.episodeBytes)}`"
      >
        <radio-icon name="download" />
        {{ megabytes(recording.episodeBytes) }}
      </a>
    </template>
    <radio-facts :rows="facts" />
    <p v-if="!recording.editList" class="mt-2 mb-0 text-sm text-ink-soft">
      The show has not been analyzed yet.
    </p>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { RecordingSummary } from '../../models/generated/domain-models'
import { radioService } from '../../services/api'
import { megabytes, textState } from '../../utils/radio'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const url = (file: string) => radioService.fileUrl(props.recording.name, file)

const facts = computed<[string, string][]>(() => {
  const text: [string, string] = ['Transcript', textState(props.recording)]
  const edit = props.recording.editList
  if (!edit) return [text]
  return [['Model', edit.outline.model ?? '—'], ['Hosts', edit.hosts.join(', ') || '—'], text]
})
</script>
