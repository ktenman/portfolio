<template>
  <div class="contents">
    <audio ref="audio" preload="metadata" />
    <transition
      enter-from-class="translate-y-full"
      leave-to-class="translate-y-full"
      enter-active-class="motion-safe:transition-transform motion-safe:duration-200"
      leave-active-class="motion-safe:transition-transform motion-safe:duration-200"
    >
      <div
        v-if="visible && (player.recording.value || player.live.value)"
        class="sticky bottom-0 z-10 mt-6 border-t border-hairline bg-surface px-3 pt-2 pb-[calc(0.5rem+env(safe-area-inset-bottom,0px))] shadow-card"
        data-testid="radio-dock"
      >
        <div v-if="!player.live.value" class="absolute inset-x-0 -top-px h-1 bg-hairline">
          <div class="h-full bg-brass" :style="{ width: `${share}%` }" />
          <input
            type="range"
            class="absolute inset-x-0 -top-1.5 h-4 w-full cursor-pointer opacity-0"
            min="0"
            :max="player.total.value"
            step="1"
            :value="player.seconds.value"
            aria-label="Asukoht salvestuses"
            :aria-valuetext="`${position(player.seconds.value)} / ${position(player.total.value)}`"
            data-testid="radio-dock-line"
            @input="player.seek(Number(($event.target as HTMLInputElement).value))"
          />
        </div>
        <div class="mx-auto flex max-w-app flex-wrap items-center gap-x-4 gap-y-2">
          <p v-if="player.live.value" class="mb-0 min-w-0 grow basis-40 font-semibold">
            Kuku otse
            <span v-if="station?.onAir" class="block text-sm font-normal text-ink-soft">
              {{ station.onAir.name }} · {{ airing(station.onAir) }}
            </span>
          </p>
          <router-link
            v-else-if="player.recording.value"
            :to="`/radio/${player.recording.value.name}`"
            class="min-w-0 grow basis-40 font-semibold"
          >
            {{ player.recording.value.programme }}
            <span class="block text-sm font-normal text-ink-soft tabular-nums">
              {{ position(player.seconds.value) }} / {{ position(player.total.value) }}
            </span>
          </router-link>
          <radio-controls />
          <app-button v-if="player.lost.value" @click="player.rejoin">Tagasi praegusele</app-button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioStation } from '../../composables/use-radio-recordings'
import type { StationAiring } from '../../models/generated/domain-models'
import { position, range } from '../../utils/radio'
import AppButton from '../shared/app-button.vue'
import RadioControls from './radio-controls.vue'

const audio = useTemplateRef<HTMLAudioElement>('audio')
const player = useRadioPlayer()
const { data: station } = useRadioStation()

const airing = (a: StationAiring) => range(a.start, 0, a.end - a.start)

const visible = computed(() => player.live.value || !player.cardInView.value)
const share = computed(() =>
  player.total.value ? (player.seconds.value / player.total.value) * 100 : 0
)

onMounted(() => audio.value && player.attach(audio.value))
onBeforeUnmount(player.detach)
</script>
