<template>
  <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_20rem]">
    <div class="grid min-w-0 gap-4">
      <radio-topics
        class="lg:hidden"
        chips
        :sections="topics"
        :started-at="recording.status.startedAt"
        :raw="raw"
        :playing="playing"
        @pick="pick"
      />
      <div class="flex flex-wrap items-center gap-2">
        <input
          v-model="search"
          type="search"
          placeholder="Otsi tekstist"
          aria-label="Otsi tekstist"
          class="min-w-48 grow rounded-container border border-control-border bg-surface px-3 py-1.5 text-ink shadow-control focus-visible:outline-2 focus-visible:outline-brass-deep"
        />
        <a v-for="file in FILES" :key="file.name" :href="url(file.name)" download :class="LINK">
          <radio-icon name="download" />
          {{ file.label }}
        </a>
      </div>
      <radio-card title="Tekst" data-testid="radio-transcript">
        <p v-if="transcript.isLoading.value" class="mb-0 text-ink-soft">Laadin…</p>
        <p v-else-if="!shown.length" class="mb-0 text-ink-soft">
          {{ search ? 'Otsingule vastavat rida pole.' : 'Teksti veel pole.' }}
        </p>
        <div v-else ref="list">
          <radio-section
            v-for="section in shown"
            :key="section.start"
            :section="section"
            :started-at="recording.status.startedAt"
            :raw="raw"
            :playing="section.start === playing?.start"
            :current="current"
            :spoken="spoken"
            @play="play(section, $event)"
          />
        </div>
      </radio-card>
    </div>
    <div class="grid gap-4">
      <radio-topics
        class="max-lg:hidden"
        :sections="topics"
        :started-at="recording.status.startedAt"
        :raw="raw"
        :playing="playing"
        @pick="pick"
      />
      <radio-result v-if="recording.editList" :recording="recording" />
      <radio-cuts v-if="raw" :recording="recording" />
      <radio-analysis :recording="recording" />
      <radio-details :recording="recording" />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useEventListener, usePreferredReducedMotion } from '@vueuse/core'
import type {
  RecordingSummary,
  TranscriptLine,
  TranscriptSection,
} from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioTranscript } from '../../composables/use-radio-recordings'
import { radioService } from '../../services/api'
import { LINK } from '../../utils/radio'
import RadioAnalysis from './radio-analysis.vue'
import RadioCard from './radio-card.vue'
import RadioCuts from './radio-cuts.vue'
import RadioDetails from './radio-details.vue'
import RadioIcon from './radio-icon.vue'
import RadioResult from './radio-result.vue'
import RadioSection from './radio-section.vue'
import RadioTopics from './radio-topics.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const KEYS = new Set(['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' '])
const DRAG = 10

const FILES = [
  { name: 'transcript.srt', label: 'SRT' },
  { name: 'transcript.txt', label: 'TXT' },
]

const transcript = useRadioTranscript(() => props.recording)
const player = useRadioPlayer()
const search = ref('')

const url = (file: string) => radioService.fileUrl(props.recording.name, file)
const raw = computed(() => player.version.value === 'raw')
const sections = computed(() => transcript.data.value ?? [])
const topics = computed(() =>
  raw.value ? sections.value : sections.value.filter(section => section.episodeStart !== null)
)
const lines = computed(() => sections.value.flatMap(section => section.lines))
const shown = computed(() => {
  const needle = search.value.trim().toLocaleLowerCase('et')
  if (!needle) return topics.value
  return topics.value
    .map(section => ({
      ...section,
      lines: section.lines.filter(line => line.text.toLocaleLowerCase('et').includes(needle)),
    }))
    .filter(section => section.lines.length)
})

const at = computed(() =>
  player.recording.value?.name === props.recording.name ? player.recordingSeconds.value : -1
)
const current = computed(() => lines.value.find(l => l.start <= at.value && at.value < l.end))
const spoken = computed(() =>
  current.value?.words.find(w => w.start <= at.value && at.value < w.end)
)
const playing = computed(() => topics.value.find(s => s.start <= at.value && at.value < s.end))

const list = useTemplateRef<HTMLElement>('list')
const motion = usePreferredReducedMotion()
let jumped = false
let touched = 0

const behavior = () => (motion.value === 'reduce' ? 'auto' : 'smooth')

const play = (section: TranscriptSection, from: number) =>
  raw.value
    ? player.playFrom(from)
    : player.playFrom((section.episodeStart ?? 0) + from - section.start, 'episode')

const pick = (section: TranscriptSection) => {
  play(section, section.start)
  list.value
    ?.querySelector(`[data-start="${section.start}"]`)
    ?.scrollIntoView({ block: 'start', behavior: behavior() })
}

const scroll = () =>
  list.value
    ?.querySelector('[data-testid="radio-text-line"][aria-current="true"]')
    ?.scrollIntoView({
      block: 'center',
      behavior: behavior(),
    })

const follow = (line?: TranscriptLine) => {
  if (!line || player.lost.value || search.value.trim()) return
  if (jumped && !player.playing.value) return
  jumped = true
  scroll()
}

const lose = () => {
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
useEventListener(window, 'wheel', lose, { passive: true })
useEventListener(window, 'touchstart', (event: TouchEvent) => (touched = finger(event)), {
  passive: true,
})
useEventListener(
  window,
  'touchmove',
  (event: TouchEvent) => Math.abs(finger(event) - touched) > DRAG && lose(),
  { passive: true }
)
useEventListener(window, 'keydown', (event: KeyboardEvent) => {
  if (KEYS.has(event.key) && !typing(event)) lose()
})
</script>
