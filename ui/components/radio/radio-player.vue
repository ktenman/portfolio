<template>
  <section class="card sticky top-0 z-10 mb-4 lg:top-[60.5px]" data-testid="radio-player">
    <div class="card-body grid gap-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <p class="mb-0 text-3xl font-semibold tabular-nums">
          {{ position(player.seconds.value) }}
          <span class="text-ink-soft max-md:text-lg max-md:font-normal">
            / {{ position(player.total.value) }}
          </span>
        </p>
        <select
          v-model="version"
          aria-label="Version"
          class="form-select form-select-sm min-h-11 w-auto rounded-full bg-position-[right_1rem_center] py-2.5 pr-10 pl-4 text-base font-medium md:hidden"
        >
          <option value="raw">Raw</option>
          <option v-if="cleaned" value="episode">Clean</option>
        </select>
        <div role="group" aria-label="Version" class="flex max-md:hidden">
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
          :programme="recording.status.programmes[0]"
          label="Position in the recording"
          @seek="player.seek"
          @play="player.playFrom($event, player.version.value)"
        />
        <p
          v-if="raw && parts.length"
          class="mb-0 flex flex-wrap gap-x-4 gap-y-1 text-sm text-ink-soft max-md:hidden"
        >
          <span class="flex items-center gap-1.5">
            <span class="size-3 rounded-control border border-hairline" :class="HATCH" />
            Removed
          </span>
          <span class="flex items-center gap-1.5">
            <span class="size-3 rounded-control border border-hairline bg-surface" />
            Kept
          </span>
        </p>
      </div>
      <radio-controls large />
    </div>
  </section>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount } from 'vue'
import type { RecordingSummary } from '../../models/generated/domain-models'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioPlayer, type Version } from '../../composables/use-radio-player'
import { episodeParts, HATCH, position, rawParts } from '../../utils/radio'
import RadioControls from './radio-controls.vue'
import RadioScrubber from './radio-scrubber.vue'

const VERSIONS = [
  { value: 'raw', label: 'Raw' },
  { value: 'episode', label: 'Clean' },
] as const

const props = defineProps<{ recording: RecordingSummary }>()

const player = useRadioPlayer()
const decisions = useRadioDecisions(() => props.recording)
onBeforeUnmount(() => {
  player.pause()
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
</script>
