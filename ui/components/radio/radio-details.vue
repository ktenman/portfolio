<template>
  <radio-card title="Processing" fold="shut" data-testid="radio-details">
    <template v-if="downloads.length" #aside>
      <span class="-mr-2 flex">
        <a
          v-for="item in downloads"
          :key="item.file"
          :href="url(item.file)"
          download
          class="inline-flex min-h-11 items-center gap-1.5 rounded-full px-3 text-sm font-medium whitespace-nowrap text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep"
          :aria-label="`Download ${item.what}, ${megabytes(item.bytes)}`"
        >
          <radio-icon name="download" />
          {{ item.label }} {{ megabytes(item.bytes) }}
        </a>
      </span>
    </template>
    <radio-facts :rows="facts" />
    <p v-if="!recording.editList" class="mt-2 mb-0 text-sm text-ink-soft">
      The show has not been analyzed yet.
    </p>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import {
  type ModelVersion,
  type RecordingSummary,
  TranscriptState,
} from '../../models/generated/domain-models'
import { radioService } from '../../services/api'
import { megabytes, STATIONS } from '../../utils/radio'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const url = (file: string) => radioService.fileUrl(props.recording.name, file)

const downloads = computed(() =>
  [
    { file: 'show.mp3', label: 'Full', what: 'the full show', bytes: props.recording.sizeBytes },
    {
      file: 'episode.m4a',
      label: 'Clean',
      what: 'the clean version',
      bytes: props.recording.episodeBytes ?? 0,
    },
  ].filter(item => item.bytes)
)

const model = (label: string, version: ModelVersion | null): [string, string, string?] => {
  if (version) return [label, version.label, `${version.model} at ${version.revision}`]
  return [
    label,
    props.recording.transcriptState === TranscriptState.RECORDING ? 'In progress' : '—',
  ]
}

const facts = computed<[string, string, string?][]>(() => {
  const station: [string, string] = ['Station', STATIONS[props.recording.status.station]]
  const models = [model('Speech', props.recording.speech), model('Sounds', props.recording.sounds)]
  const edit = props.recording.editList
  if (!edit) return [station, ...models]
  return [
    station,
    ['Analysis', edit.outline.model ?? '—'],
    ['Hosts', edit.hosts.join(', ') || '—'],
    ...models,
  ]
})
</script>
