<template>
  <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_20rem]">
    <div class="grid gap-4">
      <radio-card title="Ajajoon">
        <template #aside><span class="text-sm text-ink-soft">Masinteksti järgi</span></template>
        <ol class="mb-0 list-none p-0">
          <li
            v-for="segment in recording.timeline"
            :key="segment.start"
            class="border-b border-hairline last:border-b-0"
          >
            <button
              type="button"
              data-testid="radio-timeline-row"
              :aria-current="now(segment) ? 'true' : undefined"
              class="flex w-full cursor-pointer items-start gap-4 px-1 py-3 text-left transition-colors hover:bg-surface-hover"
              @click="player.playFrom(segment.start)"
            >
              <span class="w-12 shrink-0 tabular-nums text-ink-soft">
                {{ clock(recording.status.startedAt, segment.start) }}
              </span>
              <span class="min-w-0 grow">
                <span class="block" :class="segment.cut ? 'text-ink-soft' : 'font-semibold'">
                  {{ segment.title }}
                </span>
                <span class="block text-sm text-ink-soft">
                  {{
                    segment.cut
                      ? minutes(segment.end - segment.start)
                      : `${recording.programme} · ${minutes(segment.end - segment.start)}`
                  }}
                </span>
              </span>
              <radio-badge v-if="now(segment)" tone="live">Praegu</radio-badge>
            </button>
          </li>
        </ol>
        <p v-if="showStart !== null && showEnd !== null" class="mt-3 mb-0 text-sm text-ink-soft">
          Saade tegelikult {{ range(recording.status.startedAt, showStart, showEnd, true) }}. Hallid
          read on lõikesoovitused; teemad on iga osa algusest.
        </p>
      </radio-card>
    </div>
    <div class="grid gap-4">
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
          <router-link
            v-if="recording.editList"
            :to="`/radio/${recording.name}/cuts`"
            :class="LINK"
          >
            Vaata lõikeid
          </router-link>
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
import {
  CutKind,
  type RecordingSummary,
  type TimelineEntry,
} from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { radioService } from '../../services/api'
import {
  audioFormat,
  clock,
  cutState,
  day,
  dropNote,
  megabytes,
  LINK,
  minutes,
  position,
  range,
  span,
  STATES,
  textState,
} from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const player = useRadioPlayer()

const url = (file: string) => radioService.fileUrl(props.recording.name, file)
const showStart = computed(
  () => props.recording.cuts.find(c => c.kind === CutKind.BEFORE)?.end ?? null
)
const showEnd = computed(
  () => props.recording.cuts.find(c => c.kind === CutKind.AFTER)?.start ?? null
)
const state = computed(() => STATES[props.recording.status.state])

const now = (segment: TimelineEntry) =>
  player.recording.value?.name === props.recording.name &&
  segment.start <= player.recordingSeconds.value &&
  player.recordingSeconds.value < segment.end

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
