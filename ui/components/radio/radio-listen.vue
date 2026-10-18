<template>
  <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_20rem]">
    <div class="grid gap-4">
      <section class="card">
        <div class="card-body grid gap-4">
          <div class="flex flex-wrap items-end justify-between gap-3">
            <div>
              <p class="mb-0 text-3xl font-semibold tabular-nums">
                {{ position(player.seconds.value) }}
                <span class="text-ink-soft">/ {{ position(player.total.value) }}</span>
              </p>
              <p class="mb-0 text-sm text-ink-soft">
                Eetris {{ clock(recording.status.startedAt, player.recordingSeconds.value, true) }}
                <template v-if="!player.playing.value">· peatatud</template>
              </p>
            </div>
            <label class="flex items-center gap-2 text-sm text-ink-soft">
              Versioon
              <select v-model="version" aria-label="Versioon" :class="SELECT">
                <option value="raw">
                  Toorsalvestus · {{ position(recording.status.seconds) }}
                </option>
                <option v-if="cleaned" value="episode">
                  Puhastatud · {{ position(recording.editList?.durationSeconds ?? 0) }}
                </option>
              </select>
            </label>
          </div>
          <radio-scrubber
            :value="player.seconds.value"
            :total="player.total.value"
            :parts="player.version.value === 'raw' ? bars : undefined"
            :ticks="ticks"
            label="Asukoht salvestuses"
            @seek="player.seek"
          />
          <div class="flex flex-wrap items-center justify-between gap-3">
            <radio-controls large />
            <div class="flex flex-wrap items-center gap-2">
              <app-button
                v-if="showStart !== null"
                data-testid="radio-show-start"
                @click="player.playFrom(showStart)"
              >
                Saate algusesse · {{ clock(recording.status.startedAt, showStart) }}
              </app-button>
              <label class="flex items-center gap-2 text-sm text-ink-soft">
                Kiirus
                <select v-model.number="player.rate.value" :class="SELECT">
                  <option v-for="rate in RATES" :key="rate" :value="rate">{{ rate }}×</option>
                </select>
              </label>
            </div>
          </div>
        </div>
      </section>
      <radio-card title="Ajajoon">
        <template #aside><span class="text-sm text-ink-soft">Masinteksti järgi</span></template>
        <ol class="mb-0 list-none p-0">
          <li
            v-for="segment in timeline"
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
          Saade tegelikult {{ clock(recording.status.startedAt, showStart, true) }}–{{
            clock(recording.status.startedAt, showEnd, true)
          }}. Hallid read on lõikesoovitused; teemad on iga osa algusest.
        </p>
      </radio-card>
    </div>
    <div class="grid gap-4">
      <radio-card title="Salvestus">
        <template #aside>
          <radio-badge :tone="state.tone">{{ state.label }}</radio-badge>
        </template>
        <radio-facts :rows="recordingFacts" />
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
import { useRadioPlayer, type Version } from '../../composables/use-radio-player'
import { radioService } from '../../services/api'
import {
  clock,
  cutState,
  day,
  megabytes,
  minutes,
  position,
  span,
  STATES,
  textState,
} from '../../utils/radio'
import AppButton from '../shared/app-button.vue'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'
import RadioControls from './radio-controls.vue'
import RadioFacts from './radio-facts.vue'
import RadioIcon from './radio-icon.vue'
import RadioScrubber, { type Tick } from './radio-scrubber.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const SELECT =
  'rounded-container border border-control-border bg-surface px-3 py-1.5 text-sm text-ink shadow-control focus-visible:outline-2 focus-visible:outline-brass-deep'
const LINK =
  'inline-flex items-center gap-1.5 rounded-container border border-control-border px-3 py-1.5 text-sm font-medium shadow-control transition-colors hover:bg-brass-wash hover:text-brass-deep'
const RATES = [1, 1.25, 1.5, 2]

const player = useRadioPlayer()

const url = (file: string) => radioService.fileUrl(props.recording.name, file)
const cleaned = computed(() => Boolean(props.recording.editList && props.recording.episodeBytes))
const version = computed({
  get: () => player.version.value,
  set: (value: Version) => player.load(props.recording, value),
})
const showStart = computed(
  () => props.recording.cuts.find(c => c.kind === CutKind.BEFORE)?.end ?? null
)
const showEnd = computed(
  () => props.recording.cuts.find(c => c.kind === CutKind.AFTER)?.start ?? null
)
const timeline = computed(() => props.recording.timeline)
const bars = computed(() => timeline.value.map(({ start, end, cut }) => ({ start, end, cut })))
const state = computed(() => STATES[props.recording.status.state])

const ticks = computed<Tick[]>(() => {
  if (player.version.value !== 'raw') return []
  const { startedAt, seconds } = props.recording.status
  const marks = [showStart.value, showEnd.value].filter((at): at is number => at !== null)
  return [
    { at: 0, text: clock(startedAt, 0), phone: true },
    ...marks.map(at => ({ at, text: clock(startedAt, at) })),
    { at: seconds, text: clock(startedAt, seconds), phone: true },
  ]
})

const now = (segment: TimelineEntry) =>
  player.recording.value?.name === props.recording.name &&
  segment.start <= player.recordingSeconds.value &&
  player.recordingSeconds.value < segment.end

const recordingFacts = computed<[string, string][]>(() => [
  ['Allikas', 'Kuku otsevoog'],
  ['Aeg', `${day(props.recording.status.startedAt)} · ${span(props.recording)}`],
  ['Kestus', position(props.recording.status.seconds)],
  ['Suurus', megabytes(props.recording.sizeBytes)],
])

const workFacts = computed<[string, string][]>(() => [
  ['Tekst', textState(props.recording)],
  ['Lõiked', cutState(props.recording)],
])
</script>
