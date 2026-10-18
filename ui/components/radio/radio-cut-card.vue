<template>
  <section class="card" data-testid="radio-cut">
    <div class="card-body grid gap-3">
      <div class="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h4 class="mb-0 text-base font-semibold">{{ cut.title }}</h4>
          <p class="mb-0 text-sm text-ink-soft tabular-nums">
            {{ range(startedAt, cut.start, cut.end, true) }} ·
            {{ length(cut.end - cut.start) }}
          </p>
        </div>
        <radio-badge v-if="cut.review" tone="warn">Kontrolli</radio-badge>
      </div>
      <p v-if="quotes" class="mb-0 text-sm">{{ quotes }}</p>
      <p class="mb-0 text-sm text-ink-soft">
        Piir: {{ cut.methods.map(m => METHODS[m]).join(' · ') }}
      </p>
      <div v-if="cut.jingle" class="flex flex-wrap items-center justify-between gap-2 text-sm">
        <span class="text-ink-soft">
          Džingel r = {{ cut.jingle.r.toFixed(2).replace('.', ',') }} ·
          {{ cut.jingle.saved ? 'salvestatud' : 'kinnitamata' }}
        </span>
        <app-button
          :aria-label="`${jingleAction}: ${name}`"
          @click="emit('jingle', !cut.jingle.saved)"
        >
          {{ jingleAction }}
        </app-button>
      </div>
      <div class="flex flex-wrap items-center justify-between gap-2 border-t border-hairline pt-3">
        <div class="flex flex-wrap gap-2">
          <app-button
            :aria-label="`Kuula üleminekut: ${name}`"
            @click="player.preview(cut.start, cut.end)"
          >
            <radio-icon name="play" class="me-1.5" />
            Kuula üleminekut
          </app-button>
          <app-button
            v-if="cut.kind === CutKind.MANUAL && !cut.removed"
            :aria-label="`Kustuta: ${name}`"
            @click="emit('drop')"
          >
            Kustuta
          </app-button>
        </div>
        <div class="flex gap-2" role="group" :aria-label="`Otsus: ${name}`">
          <button
            v-for="choice in CHOICES"
            :key="choice.label"
            type="button"
            class="platform-btn"
            :class="{ active: removed === choice.removed }"
            :aria-pressed="removed === choice.removed"
            @click="emit('choose', choice.removed)"
          >
            {{ choice.label }}
          </button>
        </div>
      </div>
    </div>
  </section>
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
  { label: 'Eemalda', removed: true },
  { label: 'Jäta alles', removed: false },
]

const player = useRadioPlayer()

const quote = (line: TranscriptLine) =>
  `${clock(props.startedAt, line.start)} „${line.text.trim()}“`

const name = computed(() => `${props.cut.title} ${clock(props.startedAt, props.cut.start)}`)

const jingleAction = computed(() =>
  props.cut.jingle?.saved ? 'Unusta džingel' : 'Kinnita džingel'
)

const quotes = computed(() =>
  [props.context?.before, props.context?.after]
    .filter((line): line is TranscriptLine => Boolean(line))
    .map(quote)
    .join(' … ')
)
</script>
