<template>
  <div class="grid gap-4">
    <div class="flex flex-wrap items-center gap-2">
      <input
        v-model="search"
        type="search"
        placeholder="Otsi tekstist"
        aria-label="Otsi tekstist"
        class="min-w-48 grow rounded-container border border-control-border bg-surface px-3 py-1.5 text-ink shadow-control focus-visible:outline-2 focus-visible:outline-brass-deep"
      />
      <app-button
        :disabled="Boolean(recording.transcriptCheckedAt)"
        :loading="checking"
        @click="check"
      >
        <radio-icon name="check" />
        Märgi kontrollituks
      </app-button>
      <a v-for="file in FILES" :key="file.name" :href="url(file.name)" download :class="LINK">
        <radio-icon name="download" />
        {{ file.label }}
      </a>
    </div>
    <radio-card title="Tekst">
      <template #aside>
        <radio-badge
          data-testid="radio-text-state"
          :tone="recording.transcriptCheckedAt ? 'plain' : 'warn'"
        >
          {{ textState(recording) }}
        </radio-badge>
      </template>
      <p v-if="transcript.isLoading.value" class="mb-0 text-ink-soft">Laadin…</p>
      <p v-else-if="!shown.length" class="mb-0 text-ink-soft">
        {{ search ? 'Otsingule vastavat rida pole.' : 'Teksti veel pole.' }}
      </p>
      <ol v-else class="mb-0 list-none p-0">
        <li
          v-for="line in shown"
          :key="line.start"
          data-testid="radio-text-line"
          :aria-current="line === current ? 'true' : undefined"
          class="grid grid-cols-[4.5rem_minmax(0,1fr)] gap-3 rounded-container px-2 py-2.5"
          :class="{ 'bg-brass-wash': line === current }"
        >
          <button
            type="button"
            class="cursor-pointer self-start text-left text-sm font-semibold text-ink-soft tabular-nums hover:text-ink"
            @click="player.playFrom(line.start)"
          >
            {{ clock(recording.status.startedAt, line.start, true) }}
          </button>
          <div>
            <p class="mb-0">
              <template v-if="line === current && line.words.length">
                <template v-for="word in line.words" :key="word.start">
                  <span
                    :data-testid="word === spoken ? 'radio-word-now' : undefined"
                    :class="{ 'rounded-control bg-surface font-semibold': word === spoken }"
                  >
                    {{ word.word.trim() }}
                  </span>
                  {{ ' ' }}
                </template>
              </template>
              <template v-else>{{ line.text.trim() }}</template>
            </p>
            <p v-if="line.split" class="mt-1 mb-0 flex flex-wrap items-center gap-2 text-sm">
              <radio-badge tone="warn">Lõige poolitab lause</radio-badge>
              <span class="text-ink-soft">
                {{ line.split.title }}
                {{ clock(recording.status.startedAt, line.split.start) }}–{{
                  clock(recording.status.startedAt, line.split.end)
                }}
              </span>
            </p>
          </div>
        </li>
      </ol>
    </radio-card>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import type { RecordingSummary } from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioTranscript } from '../../composables/use-radio-recordings'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import { clock, textState } from '../../utils/radio'
import AppButton from '../shared/app-button.vue'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const LINK =
  'inline-flex items-center gap-1.5 rounded-container border border-control-border px-3 py-1.5 text-sm font-medium shadow-control transition-colors hover:bg-brass-wash hover:text-brass-deep'
const FILES = [
  { name: 'transcript.srt', label: 'SRT' },
  { name: 'transcript.txt', label: 'TXT' },
]

const transcript = useRadioTranscript(() => props.recording)
const player = useRadioPlayer()
const queryClient = useQueryClient()
const toast = useToast()
const search = ref('')
const checking = ref(false)

const url = (file: string) => radioService.fileUrl(props.recording.name, file)
const lines = computed(() => transcript.data.value ?? [])
const shown = computed(() => {
  const needle = search.value.trim().toLocaleLowerCase('et')
  if (!needle) return lines.value
  return lines.value.filter(line => line.text.toLocaleLowerCase('et').includes(needle))
})

const at = computed(() =>
  player.recording.value?.name === props.recording.name ? player.recordingSeconds.value : -1
)
const current = computed(() => lines.value.find(l => l.start <= at.value && at.value < l.end))
const spoken = computed(() =>
  current.value?.words.find(w => w.start <= at.value && at.value < w.end)
)

const check = async () => {
  checking.value = true
  await radioService
    .check(props.recording.name)
    .then(() => queryClient.invalidateQueries({ queryKey: ['radio'], exact: true }))
    .catch(() => toast.error('Teksti ei saanud kontrollituks märkida'))
    .finally(() => (checking.value = false))
}
</script>
