<template>
  <template v-if="shown">
    <div
      class="md:hidden"
      :class="lifted ? 'mb-27' : 'mb-5'"
      :style="{ height: `${size.height.value}px` }"
      aria-hidden="true"
    />
    <section
      ref="card"
      class="fixed right-2.5 bottom-2.5 z-(--z-player) rounded-xl border border-hairline bg-surface pr-1.5 pb-1.5 pl-3.5 shadow-overlay md:right-6 md:bottom-6"
      :class="[
        recording ? 'pt-0.5 max-md:left-2.5 md:w-93' : 'w-max pt-1.5',
        { 'max-md:bottom-22': lifted },
      ]"
      :style="place"
      aria-label="Radio player"
      data-testid="radio-mini-player"
    >
      <div class="flex items-center">
        <div
          ref="handle"
          class="min-w-0 grow md:cursor-grab md:touch-none md:select-none md:active:cursor-grabbing"
        >
          <p class="mb-0 truncate text-sm/4.5 font-semibold">
            {{ recording?.programme ?? STATIONS[player.tuned.value!] }}
          </p>
          <p class="mb-0 truncate text-label/4 text-brass-deep">{{ topic }}</p>
        </div>
        <router-link
          v-if="recording"
          :to="`/radio/${recording.name}`"
          :class="ICON"
          aria-label="Open the episode"
        >
          <radio-icon name="open" />
        </router-link>
        <radio-controls v-else class="ml-3" />
        <button type="button" :class="ICON" aria-label="Stop and close" @click="player.close()">
          <radio-icon name="close" />
        </button>
      </div>
      <template v-if="recording">
        <div class="mb-0.5 flex items-center gap-2 pr-2 text-label text-ink-soft tabular-nums">
          <span>{{ position(player.seconds.value) }}</span>
          <radio-scrubber
            class="min-w-0 grow"
            bare
            :value="player.seconds.value"
            :total="player.total.value"
            :parts="parts"
            label="Position in the recording"
            @seek="player.seek"
          />
          <span>{{ position(player.total.value) }}</span>
        </div>
        <radio-controls class="pr-2" />
      </template>
    </section>
  </template>
</template>

<script lang="ts" setup>
import { computed, useTemplateRef } from 'vue'
import { useRoute } from 'vue-router'
import {
  StorageSerializers,
  useDraggable,
  useElementSize,
  useLocalStorage,
  useMediaQuery,
  useWindowSize,
  type Position,
} from '@vueuse/core'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioStations } from '../../composables/use-radio-recordings'
import { STORAGE_KEYS } from '../../constants/storage-keys'
import { episodeParts, fullDay, position, rawParts, STATIONS } from '../../utils/radio'
import RadioControls from './radio-controls.vue'
import RadioIcon from './radio-icon.vue'
import RadioScrubber from './radio-scrubber.vue'

const ON_AIR_PAGES = ['Radio', 'RadioShows']

const ICON =
  'inline-flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-full text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep'

const route = useRoute()
const player = useRadioPlayer()
const { recording } = player
const { data: stations } = useRadioStations()
const decisions = useRadioDecisions(() => recording.value!)

const card = useTemplateRef('card')
const handle = useTemplateRef('handle')
const spot = useLocalStorage<Position | null>(STORAGE_KEYS.RADIO_MINI_SPOT, null, {
  serializer: StorageSerializers.object,
})
const phone = useMediaQuery('(max-width: 767px)')
const { width, height } = useWindowSize({ includeScrollbar: false })
const size = useElementSize(card, undefined, { box: 'border-box' })

const fit = ({ x, y }: Position) => ({
  x: Math.max(0, Math.min(x, width.value - size.width.value)),
  y: Math.max(0, Math.min(y, height.value - size.height.value)),
})

useDraggable(card, { handle, disabled: phone, onMove: at => (spot.value = fit(at)) })

const place = computed(() => {
  if (phone.value || !spot.value) return undefined
  const { x, y } = fit(spot.value)
  return { left: `${x}px`, top: `${y}px`, right: 'auto', bottom: 'auto' }
})

const lifted = computed(() => route.name === 'Calculator')

const shown = computed(() => {
  if (recording.value) return route.params.name !== recording.value.name
  const onAir =
    ON_AIR_PAGES.includes(String(route.name)) &&
    !route.query.q &&
    stations.value?.some(station => station.onAir)
  return !onAir
})

const parts = computed(() => {
  const target = recording.value
  if (!target) return []
  return player.version.value === 'raw'
    ? rawParts(target, decisions.removed.value)
    : episodeParts(target)
})

const topic = computed(() => {
  const target = recording.value
  if (!target) return 'Live'
  const at = player.seconds.value
  const part = parts.value.find(({ start, end }) => start <= at && at < end)
  return part?.title ?? fullDay(target.status.startedAt)
})
</script>
