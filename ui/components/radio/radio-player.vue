<template>
  <section class="card mb-4" data-testid="radio-player">
    <div class="card-body grid gap-4">
      <div class="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p class="mb-0 text-3xl font-semibold tabular-nums">
            {{ position(player.seconds.value) }}
            <span class="text-ink-soft">/ {{ position(player.total.value) }}</span>
          </p>
          <p class="mb-0 text-sm text-ink-soft">
            Eetris {{ clock(recording.status.startedAt, player.recordingSeconds.value, true) }}
          </p>
        </div>
        <label class="flex w-full items-center gap-2 text-sm text-ink-soft md:w-auto">
          Versioon
          <select
            v-model="version"
            aria-label="Versioon"
            class="form-select form-select-sm w-auto grow max-md:py-2.5 md:grow-0"
          >
            <option value="raw">Toorsalvestus · {{ position(recording.status.seconds) }}</option>
            <option v-if="cleaned" value="episode">
              Puhastatud · {{ position(recording.editList?.durationSeconds ?? 0) }}
            </option>
          </select>
        </label>
      </div>
      <div class="grid gap-2 max-md:hidden">
        <radio-strip
          :parts="parts"
          :at="player.seconds.value"
          :marks="marks"
          @play="player.playFrom($event, player.version.value)"
        />
        <p class="mb-0 flex flex-wrap gap-x-4 gap-y-1 text-sm text-ink-soft">
          <template v-if="raw">
            <span class="flex items-center gap-1.5">
              <span class="size-3 rounded-control border border-hairline" :class="HATCH" />
              Eemaldatakse
            </span>
            <span class="flex items-center gap-1.5">
              <span class="size-3 rounded-control border border-hairline bg-surface" />
              Jääb alles
            </span>
          </template>
          <span v-else-if="marks.length" class="flex items-center gap-1.5">
            <span class="h-3 w-0.5 bg-brass" />
            Peatükk
          </span>
          <span>Klõpsa osal, et seda kuulata</span>
        </p>
      </div>
      <radio-scrubber
        :value="player.seconds.value"
        :total="player.total.value"
        :parts="parts"
        :marks="marks"
        :caption="chapter"
        :ticks="ticks"
        label="Asukoht salvestuses"
        @seek="player.seek"
      />
      <div ref="controls" class="flex flex-wrap items-center justify-between gap-3">
        <radio-controls large />
        <div class="flex flex-wrap items-center gap-2">
          <app-button v-if="player.lost.value" @click="player.rejoin">Tagasi praegusele</app-button>
          <app-button
            v-if="raw && showStart !== null"
            data-testid="radio-show-start"
            class="min-h-11 md:min-h-0"
            @click="player.playFrom(showStart)"
          >
            Saate algusesse · {{ clock(recording.status.startedAt, showStart) }}
          </app-button>
          <label class="flex items-center gap-2 text-sm text-ink-soft">
            Kiirus
            <select
              v-model.number="player.rate.value"
              class="form-select form-select-sm w-auto max-md:py-2.5"
            >
              <option v-for="rate in RATES" :key="rate" :value="rate">{{ rate }}×</option>
            </select>
          </label>
        </div>
      </div>
    </div>
  </section>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, useTemplateRef, watchEffect } from 'vue'
import { useElementVisibility } from '@vueuse/core'
import { CutKind, type RecordingSummary } from '../../models/generated/domain-models'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioPlayer, type Version } from '../../composables/use-radio-player'
import { clock, episodeParts, HATCH, position, rawParts } from '../../utils/radio'
import AppButton from '../shared/app-button.vue'
import RadioControls from './radio-controls.vue'
import RadioScrubber, { type Tick } from './radio-scrubber.vue'
import RadioStrip from './radio-strip.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const RATES = [1, 1.25, 1.5, 2]

const player = useRadioPlayer()
const decisions = useRadioDecisions(() => props.recording)
const shown = useElementVisibility(useTemplateRef<HTMLElement>('controls'), {
  initialValue: true,
  rootMargin: '-56px 0px 0px 0px',
})

watchEffect(() => (player.cardInView.value = shown.value))
onBeforeUnmount(() => {
  player.cardInView.value = false
  void decisions.flush()
})

const raw = computed(() => player.version.value === 'raw')
const cleaned = computed(() => Boolean(props.recording.editList && props.recording.episodeBytes))
const version = computed({
  get: () => player.version.value,
  set: (value: Version) => player.load(props.recording, value),
})
const parts = computed(() =>
  raw.value ? rawParts(props.recording, decisions.removed.value) : episodeParts(props.recording)
)
const marks = computed(() =>
  raw.value ? [] : (props.recording.editList?.chapters ?? []).map(chapter => chapter.start)
)
const chapter = computed(() => {
  const count = marks.value.filter(mark => mark <= player.seconds.value).length
  return count ? `Peatükk ${count}` : ''
})
const showStart = computed(
  () => props.recording.cuts.find(c => c.kind === CutKind.BEFORE)?.end ?? null
)
const showEnd = computed(
  () => props.recording.cuts.find(c => c.kind === CutKind.AFTER)?.start ?? null
)

const ticks = computed<Tick[]>(() => {
  const end = player.total.value
  if (!raw.value) return [0, end].map(at => ({ at, text: position(at), phone: true }))
  const { startedAt } = props.recording.status
  const shown = [showStart.value, showEnd.value].filter((at): at is number => at !== null)
  return [
    { at: 0, text: clock(startedAt, 0), phone: true },
    ...shown.map(at => ({ at, text: clock(startedAt, at) })),
    { at: end, text: clock(startedAt, end), phone: true },
  ]
})
</script>
