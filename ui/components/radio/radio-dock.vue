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
        class="sticky bottom-0 z-10 mt-6 border-t border-hairline bg-surface px-3 pt-1.5 pb-[calc(0.5rem+env(safe-area-inset-bottom,0px))] shadow-card md:px-10"
        data-testid="radio-dock"
      >
        <div class="mx-auto grid max-w-app gap-1">
          <radio-scrubber
            v-if="!player.live.value"
            bare
            :value="player.seconds.value"
            :total="player.total.value"
            :parts="parts"
            label="Asukoht salvestuses"
            @seek="player.seek"
          />
          <component
            :is="link ? RouterLink : 'p'"
            :to="link"
            class="mb-1 flex items-baseline justify-between gap-3 md:hidden"
          >
            <span class="truncate text-sm font-semibold text-brass-deep">{{ heading }}</span>
            <span class="shrink-0 text-label text-ink-soft tabular-nums">{{ status }}</span>
          </component>
          <radio-controls>
            <component
              :is="link ? RouterLink : 'p'"
              :to="link"
              class="mb-0 min-w-0 grow max-md:hidden"
            >
              <span class="block truncate font-semibold text-brass-deep">{{ heading }}</span>
              <span class="block text-sm text-ink-soft tabular-nums">{{ detail }}</span>
            </component>
          </radio-controls>
        </div>
      </div>
    </transition>
  </div>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import { RouterLink } from 'vue-router'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioStation } from '../../composables/use-radio-recordings'
import { episodeParts, position, range, rawParts } from '../../utils/radio'
import RadioControls from './radio-controls.vue'
import RadioScrubber from './radio-scrubber.vue'

const audio = useTemplateRef<HTMLAudioElement>('audio')
const player = useRadioPlayer()
const { data: station } = useRadioStation()
const decisions = useRadioDecisions(() => player.recording.value!)

const visible = computed(() => player.live.value || !player.cardInView.value)
const link = computed(() =>
  player.recording.value ? `/radio/${player.recording.value.name}` : undefined
)
const parts = computed(() => {
  const recording = player.recording.value
  if (!recording) return []
  return player.version.value === 'raw'
    ? rawParts(recording, decisions.removed.value)
    : episodeParts(recording)
})
const here = computed(() =>
  parts.value.find(part => part.start <= player.seconds.value && player.seconds.value < part.end)
)
const onAir = computed(() => {
  const airing = station.value?.onAir
  return airing ? `${airing.name} · ${range(airing.start, 0, airing.end - airing.start)}` : ''
})
const status = computed(() =>
  player.live.value
    ? onAir.value
    : `${position(player.seconds.value)} / ${position(player.total.value)}`
)
const heading = computed(() =>
  player.live.value ? 'Kuku otse' : (here.value?.title ?? player.recording.value?.programme)
)
const detail = computed(() =>
  player.live.value ? onAir.value : `${player.recording.value?.programme} · ${status.value}`
)

onMounted(() => audio.value && player.attach(audio.value))
onBeforeUnmount(player.detach)
</script>
