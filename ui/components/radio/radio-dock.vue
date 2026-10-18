<template>
  <div>
    <audio ref="audio" preload="metadata" />
    <div
      v-if="visible && (player.recording.value || player.live.value)"
      class="sticky bottom-0 z-10 mt-6 border-t border-hairline bg-surface px-3 pt-2 pb-[calc(0.5rem+env(safe-area-inset-bottom,0px))] shadow-card"
      data-testid="radio-dock"
    >
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
        <radio-scrubber
          v-if="!player.live.value"
          class="w-full md:w-auto md:grow"
          :value="player.seconds.value"
          :total="player.total.value"
          label="Asukoht salvestuses"
          @seek="player.seek"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import { useRoute } from 'vue-router'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioStation } from '../../composables/use-radio-recordings'
import type { StationAiring } from '../../models/generated/domain-models'
import { position, range } from '../../utils/radio'
import RadioControls from './radio-controls.vue'
import RadioScrubber from './radio-scrubber.vue'

const audio = useTemplateRef<HTMLAudioElement>('audio')
const route = useRoute()
const player = useRadioPlayer()
const { data: station } = useRadioStation()

const airing = (a: StationAiring) => range(a.start, 0, a.end - a.start)

const visible = computed(() => route.name !== 'RadioListen')

onMounted(() => audio.value && player.attach(audio.value))
onBeforeUnmount(player.detach)
</script>
