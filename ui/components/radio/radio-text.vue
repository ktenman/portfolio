<template>
  <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_20rem]">
    <div ref="main" class="grid min-w-0 gap-4 max-md:hidden" data-testid="radio-transcript">
      <radio-card title="Transcript">
        <p v-if="transcript.isLoading.value" class="mb-0 text-ink-soft">Loading…</p>
        <p v-else-if="!topics.length" class="mb-0 text-ink-soft">No transcript yet.</p>
        <div v-else ref="list">
          <radio-section
            v-for="section in topics"
            :key="section.start"
            :section="section"
            :started-at="recording.status.startedAt"
            :raw="raw"
            :playing="section.start === playing?.start"
            :current="current"
            :spoken="spoken"
            @play="play(section, $event)"
          >
            <template v-if="raw && cutOf(section)" #heading>
              <div :class="HATCH" class="rounded-container px-3">
                <radio-cut-card
                  :cut="cutOf(section)!"
                  :context="contexts.data.value?.[section.cut!]"
                  :started-at="recording.status.startedAt"
                  :removed="decisions.removed.value.includes(section.cut!)"
                  @choose="decisions.choose(section.cut!, $event)"
                  @jingle="jingle(section.cut!, $event)"
                  @drop="drop(section.cut!)"
                />
              </div>
            </template>
          </radio-section>
        </div>
      </radio-card>
    </div>
    <div
      ref="rail"
      data-testid="radio-rail"
      class="[scrollbar-width:thin]"
      :class="{
        'lg:sticky lg:top-[calc(var(--radio-player)+5rem)] lg:max-h-[calc(100dvh-6rem-var(--radio-player))] lg:overflow-y-auto':
          long,
      }"
    >
      <div ref="body" class="grid gap-4">
        <radio-topics
          :parts="parts"
          :at="player.seconds.value"
          @play="player.playFrom($event, player.version.value)"
        />
        <radio-result v-if="raw && recording.editList" :recording="recording" />
        <radio-cuts v-if="raw" :recording="recording" />
        <radio-details :recording="recording" />
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, useTemplateRef, watch } from 'vue'
import { useElementSize, useEventListener, usePreferredReducedMotion } from '@vueuse/core'
import { useQueryClient } from '@tanstack/vue-query'
import type {
  RecordingSummary,
  TranscriptLine,
  TranscriptSection,
} from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioCuts, useRadioTranscript } from '../../composables/use-radio-recordings'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import { episodeParts, HATCH, rawParts } from '../../utils/radio'
import RadioCard from './radio-card.vue'
import RadioCutCard from './radio-cut-card.vue'
import RadioCuts from './radio-cuts.vue'
import RadioDetails from './radio-details.vue'
import RadioResult from './radio-result.vue'
import RadioSection from './radio-section.vue'
import RadioTopics from './radio-topics.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const KEYS = new Set(['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' '])
const DRAG = 10

const transcript = useRadioTranscript(() => props.recording)
const contexts = useRadioCuts(() => props.recording)
const decisions = useRadioDecisions(() => props.recording)
const queryClient = useQueryClient()
const toast = useToast()

const cutOf = (section: TranscriptSection) =>
  section.cut === null ? undefined : props.recording.cuts[section.cut]

const jingle = (index: number, saved: boolean) =>
  radioService
    .jingle(props.recording.name, index, { saved })
    .then(() => {
      toast.success(saved ? 'Jingle saved' : 'Jingle forgotten')
      return queryClient.invalidateQueries({ queryKey: ['radio'], exact: true })
    })
    .catch(() => toast.error(saved ? 'Could not save the jingle' : 'Could not forget the jingle'))

const drop = (index: number) =>
  decisions
    .flush()
    .then(() => radioService.dropCut(props.recording.name, index))
    .then(() => queryClient.invalidateQueries({ queryKey: ['radio'] }))
    .catch(() => toast.error('Could not delete the cut'))
const player = useRadioPlayer()

const raw = computed(() => player.version.value === 'raw')
const sections = computed(() => transcript.data.value ?? [])
const topics = computed(() =>
  raw.value ? sections.value : sections.value.filter(section => section.episodeStart !== null)
)
const lines = computed(() => sections.value.flatMap(section => section.lines))
const parts = computed(() =>
  raw.value ? rawParts(props.recording, decisions.removed.value) : episodeParts(props.recording)
)

const at = computed(() =>
  player.recording.value?.name === props.recording.name ? player.recordingSeconds.value : -1
)
const current = computed(() => lines.value.find(l => l.start <= at.value && at.value < l.end))
const spoken = computed(() =>
  current.value?.words.find(w => w.start <= at.value && at.value < w.end)
)
const playing = computed(() => topics.value.find(s => s.start <= at.value && at.value < s.end))

const list = useTemplateRef<HTMLElement>('list')
const rail = useTemplateRef<HTMLElement>('rail')
const { height: text } = useElementSize(useTemplateRef<HTMLElement>('main'))
const { height: cards } = useElementSize(useTemplateRef<HTMLElement>('body'))
const long = computed(() => text.value > cards.value)
const motion = usePreferredReducedMotion()
let jumped = false
let touched = 0

const behavior = () => (motion.value === 'reduce' ? 'auto' : 'smooth')

const play = (section: TranscriptSection, from: number) =>
  raw.value
    ? player.playFrom(from)
    : player.playFrom((section.episodeStart ?? 0) + from - section.start, 'episode')

const scroll = () =>
  list.value
    ?.querySelector('[data-testid="radio-text-line"][aria-current="true"]')
    ?.scrollIntoView({
      block: 'center',
      behavior: behavior(),
    })

const follow = (line?: TranscriptLine) => {
  if (!line || player.lost.value) return
  if (jumped && !player.playing.value) return
  jumped = true
  scroll()
}

const inside = ({ target }: Event) =>
  target instanceof Node &&
  !!rail.value?.contains(target) &&
  rail.value.scrollHeight > rail.value.clientHeight

const lose = (event: Event) => {
  if (inside(event)) return
  if (current.value || (player.playing.value && at.value >= 0)) player.lost.value = true
}

const typing = ({ key, target }: KeyboardEvent) =>
  target instanceof Element &&
  target.closest(key === ' ' ? 'input, textarea, select, button' : 'input, textarea, select') !==
    null

const finger = (event: TouchEvent) => event.touches[0]?.clientY ?? touched

watch(current, follow, { flush: 'post' })
watch(player.lost, lost => lost || scroll(), { flush: 'post' })
onMounted(() => follow(current.value))
onBeforeUnmount(player.rejoin)
onBeforeUnmount(decisions.flush)
useEventListener(window, 'wheel', lose, { passive: true })
useEventListener(window, 'touchstart', (event: TouchEvent) => (touched = finger(event)), {
  passive: true,
})
useEventListener(
  window,
  'touchmove',
  (event: TouchEvent) => Math.abs(finger(event) - touched) > DRAG && lose(event),
  { passive: true }
)
useEventListener(window, 'keydown', (event: KeyboardEvent) => {
  if (KEYS.has(event.key) && !typing(event)) lose(event)
})
</script>
