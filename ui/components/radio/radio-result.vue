<template>
  <radio-card title="Tulemus">
    <div data-testid="radio-cut-result">
      <radio-facts :rows="facts" />
    </div>
    <div v-if="rendered" class="mt-3 grid gap-2">
      <div
        role="progressbar"
        aria-label="Puhastatud versioon"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-valuenow="rendered.percent"
        :aria-valuetext="`${rendered.percent} %, ${UPDATING}, ${left(rendered.secondsLeft)}`"
        class="h-1.5 overflow-hidden rounded-full bg-hairline"
      >
        <div
          class="h-full rounded-full bg-brass transition-[width] duration-700 ease-out motion-reduce:transition-none"
          :style="{ width: `${rendered.percent}%` }"
        />
      </div>
      <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 text-sm">
        <span>{{ UPDATING }} · {{ rendered.percent }} %</span>
        <span class="text-ink-soft tabular-nums">{{ left(rendered.secondsLeft) }}</span>
      </div>
    </div>
    <p v-else-if="decisions.waiting.value" class="mt-3 mb-0 text-sm">{{ UPDATING }}</p>
    <p v-else-if="recording.renderFailure" class="mt-3 mb-0 flex items-center gap-1.5 text-sm">
      <radio-icon name="alert" />
      Puhastatud versiooni ei saanud teha: {{ recording.renderFailure.reason }}
    </p>
    <p
      v-else-if="recording.episodeBytes"
      class="mt-3 mb-0 flex items-center gap-1.5 text-sm text-ink-soft"
    >
      <radio-icon name="check" />
      Puhastatud versioon on ajakohane
    </p>
    <div class="mt-3 flex flex-wrap gap-2">
      <app-button :disabled="machine" @click="decisions.reset">Lähtesta</app-button>
    </div>
    <p class="mt-3 mb-0 text-sm text-ink-soft">
      Toorsalvestus jääb muutmata. Otsused salvestuvad ise.
    </p>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { RecordingSummary } from '../../models/generated/domain-models'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useElapsed } from '../../composables/use-radio-recordings'
import { advance, left, position } from '../../utils/radio'
import AppButton from '../shared/app-button.vue'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const UPDATING = 'Uuendan puhastatud versiooni'

const decisions = useRadioDecisions(() => props.recording)

const removed = computed(() => decisions.removed.value)
const machine = computed(() => removed.value.length === props.recording.cuts.length)
const cutSeconds = computed(() =>
  props.recording.cuts
    .filter((_, i) => removed.value.includes(i))
    .reduce((sum, cut) => sum + cut.end - cut.start, 0)
)

const elapsed = useElapsed(() => props.recording.render)
const rendered = computed(() => {
  const render = props.recording.render
  if (!render) return null
  const { percent, secondsLeft } = advance(render, elapsed.value)
  return { percent: Math.min(percent, Math.floor(render.percent / 10) * 10 + 9), secondsLeft }
})

const facts = computed<[string, string][]>(() => [
  ['Toorsalvestus', position(props.recording.status.seconds)],
  ['Eemaldatakse', `${position(cutSeconds.value)} · ${removed.value.length} lõiget`],
  ['Puhastatud', position(props.recording.status.seconds - cutSeconds.value)],
])
</script>
