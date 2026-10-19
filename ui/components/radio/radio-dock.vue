<template>
  <div class="contents">
    <audio ref="audio" preload="metadata" />
    <div
      v-if="player.recording.value || player.live.value"
      class="sticky bottom-0 z-10 mt-6 border-t border-hairline bg-surface px-3 pt-1 pb-[calc(0.5rem+env(safe-area-inset-bottom,0px))] shadow-card md:pt-2"
      data-testid="radio-dock"
    >
      <app-button
        v-if="player.lost.value"
        class="absolute bottom-full left-1/2 mb-2 -translate-x-1/2 whitespace-nowrap shadow-card"
        @click="player.rejoin"
      >
        Tagasi praegusele
      </app-button>
      <div class="mx-auto max-w-app">
        <radio-scrubber
          v-if="player.recording.value"
          class="md:mb-2"
          :value="player.seconds.value"
          :total="player.total.value"
          :parts="parts"
          label="Asukoht salvestuses"
          @seek="player.seek"
        />
        <div
          class="flex flex-wrap items-center gap-x-4 gap-y-2 md:grid md:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)]"
        >
          <p v-if="player.live.value" :class="TITLE">
            Kuku otse
            <span v-if="station?.onAir" class="block text-sm font-normal text-ink-soft">
              {{ station.onAir.name }} · {{ airing(station.onAir) }}
            </span>
          </p>
          <div v-else-if="player.recording.value" :class="TITLE">
            <div class="flex min-w-0 items-center gap-2">
              <router-link :to="`/radio/${player.recording.value.name}`" class="truncate">
                {{ player.recording.value.programme }}
              </router-link>
              <select
                v-if="cleaned"
                v-model="version"
                aria-label="Versioon"
                class="form-select form-select-sm w-auto font-normal"
              >
                <option value="episode">Puhastatud</option>
                <option value="raw">Toorsalvestus</option>
              </select>
            </div>
            <span class="block text-sm font-normal text-ink-soft tabular-nums">
              {{ position(player.seconds.value) }} / {{ position(player.total.value) }} · eetris
              {{ clock(player.recording.value.status.startedAt, player.recordingSeconds.value) }}
            </span>
          </div>
          <radio-controls />
          <div class="ml-auto flex items-center gap-2 md:justify-self-end">
            <button
              type="button"
              class="inline-flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-full text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep md:size-9"
              aria-label="Vaigista"
              :aria-pressed="player.muted.value"
              @click="player.muted.value = !player.muted.value"
            >
              <radio-icon :name="player.muted.value ? 'muted' : 'volume'" />
            </button>
            <input
              v-model.number="player.volume.value"
              type="range"
              class="hidden w-20 cursor-pointer accent-brass md:inline"
              min="0"
              max="1"
              step="0.05"
              aria-label="Helitugevus"
            />
            <select
              v-if="!player.live.value"
              v-model.number="player.rate.value"
              aria-label="Kiirus"
              class="form-select form-select-sm w-auto shrink-0"
            >
              <option v-for="rate in RATES" :key="rate" :value="rate">{{ rate }}×</option>
            </select>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import { useRadioPlayer, type Version } from '../../composables/use-radio-player'
import { useRadioStation } from '../../composables/use-radio-recordings'
import type { StationAiring } from '../../models/generated/domain-models'
import { clock, position, range } from '../../utils/radio'
import AppButton from '../shared/app-button.vue'
import RadioControls from './radio-controls.vue'
import RadioIcon from './radio-icon.vue'
import RadioScrubber from './radio-scrubber.vue'

const RATES = [1, 1.25, 1.5, 2]
const TITLE = 'order-last mb-0 min-w-0 basis-full font-semibold md:order-none md:basis-auto'

const audio = useTemplateRef<HTMLAudioElement>('audio')
const player = useRadioPlayer()
const { data: station } = useRadioStation()

const airing = (a: StationAiring) => range(a.start, 0, a.end - a.start)

const cleaned = computed(() =>
  Boolean(player.recording.value?.editList && player.recording.value.episodeBytes)
)
const parts = computed(() => {
  const target = player.recording.value
  if (!target) return undefined
  if (player.version.value === 'raw') return target.timeline
  const total = target.editList?.durationSeconds ?? 0
  const starts = (target.editList?.chapters ?? []).map(c => c.start).filter(s => s > 0 && s < total)
  const marks = [0, ...new Set(starts)].sort((a, b) => a - b)
  return marks.map((start, i) => ({ start, end: marks[i + 1] ?? total, cut: false }))
})
const version = computed({
  get: () => player.version.value,
  set: (value: Version) => player.recording.value && player.load(player.recording.value, value),
})

onMounted(() => audio.value && player.attach(audio.value))
onBeforeUnmount(player.detach)
</script>
