<template>
  <section class="card mb-4" data-testid="radio-player">
    <div class="card-body grid gap-4">
      <div
        class="flex flex-wrap items-end justify-between gap-3 max-md:grid max-md:grid-cols-[1fr_auto] max-md:items-center max-md:gap-y-4"
      >
        <div class="max-md:contents">
          <p class="mb-0 text-3xl font-semibold tabular-nums max-md:col-span-2 max-md:row-start-2">
            {{ position(player.seconds.value) }}
            <span class="text-ink-soft max-md:text-lg max-md:font-normal">
              / {{ position(player.total.value) }}
            </span>
          </p>
          <p class="mb-0 text-sm text-ink-soft max-md:row-start-1">
            Eetris
            <span class="max-md:font-medium max-md:text-ink">
              {{ clock(recording.status.startedAt, player.recordingSeconds.value, true) }}
            </span>
          </p>
        </div>
        <select
          v-model="version"
          aria-label="Versioon"
          class="form-select form-select-sm min-h-11 w-auto rounded-full bg-position-[right_1rem_center] py-2.5 pr-10 pl-4 font-medium max-md:row-start-1 md:hidden"
        >
          <option value="raw">Toorsalvestus</option>
          <option v-if="cleaned" value="episode">Puhastatud</option>
        </select>
        <div role="group" aria-label="Versioon" class="flex max-md:hidden">
          <button
            v-for="option in VERSIONS"
            :key="option.value"
            type="button"
            class="relative cursor-pointer border border-hairline bg-surface-sunken px-3 py-1.5 text-sm font-medium text-ink-soft transition-colors first:rounded-l-container last:-ml-px last:rounded-r-container focus-visible:z-2 enabled:hover:not-aria-pressed:bg-surface-hover enabled:hover:not-aria-pressed:text-ink disabled:cursor-not-allowed disabled:text-ink-faint aria-pressed:z-1 aria-pressed:border-brass aria-pressed:bg-brass-wash aria-pressed:text-brass-deep"
            :aria-pressed="version === option.value"
            :disabled="option.value === 'episode' && !cleaned"
            @click="version = option.value"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
      <div class="grid gap-2">
        <radio-scrubber
          :value="player.seconds.value"
          :total="player.total.value"
          :parts="parts"
          label="Asukoht salvestuses"
          @seek="player.seek"
          @play="player.playFrom($event, player.version.value)"
        />
        <p
          v-if="raw && parts.length"
          class="mb-0 flex flex-wrap gap-x-4 gap-y-1 text-sm text-ink-soft max-md:hidden"
        >
          <span class="flex items-center gap-1.5">
            <span class="size-3 rounded-control border border-hairline" :class="HATCH" />
            Eemaldatakse
          </span>
          <span class="flex items-center gap-1.5">
            <span class="size-3 rounded-control border border-hairline bg-surface" />
            Jääb alles
          </span>
        </p>
      </div>
      <radio-now
        v-if="!raw"
        :parts="parts"
        :at="player.seconds.value"
        @play="player.playFrom($event, player.version.value)"
      />
      <div ref="controls" class="grid gap-3">
        <radio-controls large />
        <div v-if="offerStart" class="flex flex-wrap justify-center gap-2">
          <app-button
            v-if="raw && showStart !== null"
            data-testid="radio-show-start"
            class="min-h-11 max-md:grow md:min-h-0"
            @click="player.playFrom(showStart)"
          >
            Saate algusesse · {{ clock(recording.status.startedAt, showStart) }}
          </app-button>
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
import RadioNow from './radio-now.vue'
import RadioScrubber from './radio-scrubber.vue'

const VERSIONS = [
  { value: 'raw', label: 'Toorsalvestus' },
  { value: 'episode', label: 'Puhastatud' },
] as const

const props = defineProps<{ recording: RecordingSummary }>()

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
  set: (value: Version) => player.swap(value),
})
const parts = computed(() =>
  raw.value ? rawParts(props.recording, decisions.removed.value) : episodeParts(props.recording)
)
const showStart = computed(
  () => props.recording.cuts.find(c => c.kind === CutKind.BEFORE)?.end ?? null
)
const offerStart = computed(() => raw.value && showStart.value !== null)
</script>
