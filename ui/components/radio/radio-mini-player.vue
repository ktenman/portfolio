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
import { computed, onScopeDispose, useTemplateRef, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { clamp, useDraggable, useElementSize, useMediaQuery, useWindowSize } from '@vueuse/core'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRailStations } from '../../composables/use-radio-recordings'
import { episodeParts, fullDay, position, rawParts, STATIONS } from '../../utils/radio'
import RadioControls from './radio-controls.vue'
import RadioIcon from './radio-icon.vue'
import RadioScrubber from './radio-scrubber.vue'

const ON_AIR_PAGES = ['Radio', 'RadioShows']

const ICON =
  'inline-flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-full text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep'

const route = useRoute()
const player = useRadioPlayer()
const { recording, spot } = player
const stations = useRailStations()
const decisions = useRadioDecisions(() => recording.value!)

const card = useTemplateRef('card')
const handle = useTemplateRef('handle')
const phone = useMediaQuery('(max-width: 767px)')
const { width, height } = useWindowSize({ includeScrollbar: false })
const size = useElementSize(card, undefined, { box: 'border-box' })

const room = computed(() => ({
  x: Math.max(0, width.value - size.width.value),
  y: Math.max(0, height.value - size.height.value),
}))

const share = (at: number, free: number) => (free ? clamp(at / free, 0, 1) : 0)

let dragged = false

useDraggable(card, {
  handle,
  disabled: phone,
  onMove: ({ x, y }) => {
    dragged = true
    spot.value = { x: share(x, room.value.x), y: share(y, room.value.y) }
  },
  onEnd: () => {
    if (dragged && spot.value) player.pin(spot.value)
    dragged = false
  },
})

const place = computed(() => {
  if (phone.value || !spot.value) return undefined
  const { x, y } = spot.value
  return {
    left: `${x * room.value.x}px`,
    top: `${y * room.value.y}px`,
    right: 'auto',
    bottom: 'auto',
  }
})

const lifted = computed(() => route.name === 'Calculator')

const shown = computed(() => {
  if (recording.value) return route.params.name !== recording.value.name
  const onPage = ON_AIR_PAGES.includes(String(route.name)) && !route.query.q
  return !onPage || !stations.value.some(s => s.onAir && s.station === player.tuned.value)
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

const session = 'mediaSession' in navigator ? navigator.mediaSession : null
const ACTIONS: MediaSessionAction[] = ['play', 'pause', 'seekbackward', 'seekforward', 'seekto']

if (session) {
  watchEffect(() => {
    const station = recording.value?.status.station ?? player.tuned.value
    session.metadata = new MediaMetadata({
      title: recording.value?.programme ?? 'Live',
      artist: station ? STATIONS[station] : '',
      album: topic.value,
    })
  })
  watchEffect(() => {
    const seekable = !player.live.value
    session.setActionHandler('play', () => player.playing.value || player.toggle())
    session.setActionHandler('pause', () => player.playing.value && player.toggle())
    session.setActionHandler('seekbackward', seekable ? () => player.skip(-15) : null)
    session.setActionHandler('seekforward', seekable ? () => player.skip(30) : null)
    session.setActionHandler(
      'seekto',
      seekable ? ({ seekTime }) => player.seek(seekTime ?? 0) : null
    )
  })
  watchEffect(() => {
    const duration = player.total.value
    if (player.live.value || !duration) return session.setPositionState()
    session.setPositionState({ duration, position: Math.min(player.seconds.value, duration) })
  })
  onScopeDispose(() => {
    session.metadata = null
    ACTIONS.forEach(action => session.setActionHandler(action, null))
  })
}
</script>
