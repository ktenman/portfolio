<template>
  <div class="grid gap-2 border-b border-hairline py-3 last:border-b-0" data-testid="radio-cut">
    <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
      <button
        type="button"
        class="inline-flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-full border border-control-border bg-surface-sunken text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep md:size-9"
        :aria-label="`Listen to the transition: ${name}`"
        :title="`Listen to the transition: ${name}`"
        @click="player.preview(cut.start, cut.end)"
      >
        <radio-icon name="play" />
      </button>
      <div class="min-w-0 grow">
        <div class="flex flex-wrap items-center gap-2">
          <h4 class="mb-0 text-base font-semibold">{{ cut.title }}</h4>
          <radio-badge v-if="cut.review" tone="warn">Check</radio-badge>
        </div>
        <p class="mb-0 text-sm text-ink-soft tabular-nums">
          {{ range(startedAt, cut.start, cut.end, true) }} ·
          {{ length(cut.end - cut.start) }}
        </p>
      </div>
      <div
        class="grid w-full grid-cols-2 gap-2 md:flex md:w-auto"
        role="group"
        :aria-label="`Decision: ${name}`"
      >
        <button
          v-for="choice in CHOICES"
          :key="choice.label"
          type="button"
          class="platform-btn min-h-11 justify-center md:min-h-0"
          :class="{ active: removed === choice.removed }"
          :aria-pressed="removed === choice.removed"
          @click="emit('choose', choice.removed)"
        >
          {{ choice.label }}
        </button>
      </div>
    </div>
    <p v-if="quotes" class="mb-0 text-sm">{{ quotes }}</p>
    <p class="mb-0 text-sm text-ink-soft">
      Boundary: {{ cut.methods.map(m => METHODS[m]).join(' · ') }}
    </p>
    <div v-if="cut.jingle" class="flex flex-wrap items-center justify-between gap-2 text-sm">
      <span class="text-ink-soft">
        Jingle r = {{ cut.jingle.r.toFixed(2) }} ·
        {{ cut.jingle.saved ? 'saved' : 'unconfirmed' }}
      </span>
      <app-button
        :aria-label="`${jingleAction}: ${name}`"
        @click="emit('jingle', !cut.jingle.saved)"
      >
        {{ jingleAction }}
      </app-button>
    </div>
    <div v-if="cut.kind === CutKind.MANUAL && !removed">
      <app-button :aria-label="`Delete: ${name}`" @click="emit('drop')">Delete</app-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { CutKind } from '../../models/generated/domain-models'
import type { Cut, CutContext, TranscriptLine } from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { clock, length, METHODS, range } from '../../utils/radio'
import AppButton from '../shared/app-button.vue'
import RadioBadge from './radio-badge.vue'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{
  cut: Cut
  context?: CutContext
  startedAt: number
  removed: boolean
}>()

const emit = defineEmits<{ choose: [removed: boolean]; jingle: [saved: boolean]; drop: [] }>()

const CHOICES = [
  { label: 'Remove', removed: true },
  { label: 'Keep', removed: false },
]

const player = useRadioPlayer()

const quote = (line: TranscriptLine) =>
  `${clock(props.startedAt, line.start)} “${line.text.trim()}”`

const name = computed(() => `${props.cut.title} ${clock(props.startedAt, props.cut.start)}`)

const jingleAction = computed(() => (props.cut.jingle?.saved ? 'Forget jingle' : 'Confirm jingle'))

const quotes = computed(() =>
  [props.context?.before, props.context?.after]
    .filter((line): line is TranscriptLine => Boolean(line))
    .map(quote)
    .join(' … ')
)
</script>
