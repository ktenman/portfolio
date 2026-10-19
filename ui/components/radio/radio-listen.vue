<template>
  <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_20rem]">
    <radio-cuts v-if="raw" :recording="recording" />
    <radio-chapters v-else :recording="recording" />
    <div class="grid gap-4">
      <radio-result v-if="recording.editList" :recording="recording" />
      <radio-card title="Salvestus">
        <template #aside>
          <radio-badge :tone="state.tone">{{ state.label }}</radio-badge>
        </template>
        <radio-facts :rows="recordingFacts" />
        <p v-if="recording.drops" class="mt-2 mb-0 text-sm text-ink-soft">
          {{ dropNote(recording) }}
        </p>
        <div class="mt-3 flex flex-wrap gap-2">
          <a
            v-for="(part, i) in recording.status.parts"
            :key="part.file"
            :href="url(part.file)"
            download
            :class="LINK"
          >
            <radio-icon name="download" />
            {{ recording.status.parts.length > 1 ? `Osa ${i + 1}` : 'Laadi alla MP3' }}
          </a>
        </div>
      </radio-card>
      <radio-card title="Töötlus">
        <radio-facts :rows="workFacts" />
        <div class="mt-3 flex flex-wrap gap-2">
          <a v-if="recording.episodeBytes" :href="url('episode.m4a')" download :class="LINK">
            <radio-icon name="download" />
            Puhastatud · {{ megabytes(recording.episodeBytes) }}
          </a>
        </div>
      </radio-card>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { RecordingSummary } from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { radioService } from '../../services/api'
import {
  audioFormat,
  cutState,
  day,
  dropNote,
  megabytes,
  LINK,
  position,
  span,
  STATES,
  textState,
} from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'
import RadioChapters from './radio-chapters.vue'
import RadioCuts from './radio-cuts.vue'
import RadioFacts from './radio-facts.vue'
import RadioIcon from './radio-icon.vue'
import RadioResult from './radio-result.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const player = useRadioPlayer()

const raw = computed(() => player.version.value === 'raw')
const url = (file: string) => radioService.fileUrl(props.recording.name, file)
const state = computed(() => STATES[props.recording.status.state])
const recordingFacts = computed<[string, string][]>(() => [
  ['Allikas', 'Kuku otsevoog'],
  ['Aeg', `${day(props.recording.status.startedAt)} · ${span(props.recording)}`],
  ['Kestus', position(props.recording.status.seconds)],
  ['Vorming', audioFormat(props.recording.format)],
  ['Suurus', megabytes(props.recording.sizeBytes)],
  ...props.recording.status.parts.map((part, i, parts): [string, string] => [
    parts.length > 1 ? `Osa ${i + 1} SHA-256` : 'SHA-256',
    part.sha256?.slice(0, 12) ?? '—',
  ]),
])

const workFacts = computed<[string, string][]>(() => [
  ['Tekst', textState(props.recording)],
  ['Lõiked', cutState(props.recording)],
])
</script>
