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
      class="fixed right-2.5 bottom-2.5 z-(--z-player) overflow-hidden rounded-xl border border-hairline bg-surface py-1.5 pr-1.5 shadow-overlay md:right-6 md:bottom-6"
      :class="[
        recording ? 'pl-1.5 max-md:left-2.5 md:w-93' : 'w-max pl-3.5',
        { 'max-md:bottom-22': lifted, [SETTLE]: dropped },
      ]"
      :style="place"
      aria-label="Radio player"
      data-testid="radio-mini-player"
      @transitionend.self="dropped = false"
    >
      <div class="flex items-center">
        <button
          v-if="recording"
          type="button"
          data-testid="radio-play"
          :class="[LOADED, 'size-11 [&>svg]:size-5']"
          :aria-label="player.playing.value ? 'Pause' : 'Play'"
          @click="player.toggle()"
        >
          <radio-ring />
          <radio-icon :name="player.playing.value ? 'pause' : 'play'" />
        </button>
        <div
          ref="handle"
          class="min-w-0 grow md:cursor-grab md:touch-none md:select-none md:active:cursor-grabbing"
          @click.capture="hold"
        >
          <router-link
            v-if="recording"
            :to="`/radio/${recording.name}`"
            draggable="false"
            class="group block rounded-control px-2 text-ink no-underline focus-visible:outline-2 focus-visible:outline-brass-deep"
          >
            <p class="mb-0 truncate text-sm/4.5 font-semibold group-hover:underline">
              {{ topic }}
            </p>
            <p class="mb-0 flex text-label/4 text-ink-soft tabular-nums">
              <span class="truncate">{{ recording.programme }}</span>
              <span class="shrink-0">{{ time }}</span>
            </p>
          </router-link>
          <template v-else>
            <p class="mb-0 truncate text-sm/4.5 font-semibold">
              {{ STATIONS[player.tuned.value!] }}
            </p>
            <p class="mb-0 truncate text-label/4 text-ink">{{ topic }}</p>
          </template>
        </div>
        <template v-if="recording">
          <button
            type="button"
            :class="[ROUND, 'size-11 flex-col text-label']"
            aria-label="Back 15 s"
            @click="player.skip(-15)"
          >
            <radio-icon name="back" />
            15
          </button>
          <button
            type="button"
            :class="[ROUND, 'size-11 flex-col-reverse text-label']"
            aria-label="Forward 30 s"
            @click="player.skip(30)"
          >
            30
            <radio-icon name="forward" />
          </button>
        </template>
        <radio-controls v-else class="ml-3" />
        <button type="button" :class="ICON" aria-label="Stop and close" @click="player.close()">
          <radio-icon name="close" />
        </button>
      </div>
      <div
        v-if="recording"
        class="absolute inset-x-0 bottom-0 flex h-0.75 gap-px"
        aria-hidden="true"
      >
        <div
          v-for="part in parts"
          :key="part.start"
          class="min-w-0 basis-0 bg-hairline"
          :style="{ flexGrow: part.end - part.start }"
        >
          <div class="h-full bg-brass" :style="{ width: `${played(part)}%` }" />
        </div>
      </div>
    </section>
  </template>
</template>

<script lang="ts" setup>
import { computed, onScopeDispose, ref, useTemplateRef, watch, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { clamp, useDraggable, useElementSize, useMediaQuery, useWindowSize } from '@vueuse/core'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioDigests, useRailStations } from '../../composables/use-radio-recordings'
import type { RadioSpot } from '../../models/generated/domain-models'
import {
  episodeParts,
  fullDay,
  isDigest,
  LOADED,
  position,
  rawParts,
  ROUND,
  STATIONS,
  type StripPart,
} from '../../utils/radio'
import RadioControls from './radio-controls.vue'
import RadioIcon from './radio-icon.vue'
import RadioRing from './radio-ring.vue'

const ON_AIR_PAGES = ['Radio', 'RadioShows']

const EDGE = 24

const SLOP = 4

const SETTLE =
  'md:transition-[left,top] md:duration-500 md:ease-(--ease-spring) motion-reduce:transition-none'

const ICON =
  'inline-flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-full text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep'

const route = useRoute()
const player = useRadioPlayer()
const { recording, spot } = player
const stations = useRailStations()
const decisions = useRadioDecisions(() => recording.value!)
const { data: digests } = useRadioDigests(() => isDigest(recording.value?.name ?? ''))

watch(digests, list => {
  const fresh = list?.find(found => found.recording.name === recording.value?.name)?.recording
  if (fresh) player.load(fresh, 'episode')
})

const card = useTemplateRef('card')
const handle = useTemplateRef('handle')
const phone = useMediaQuery('(max-width: 767px)')
const { width, height } = useWindowSize({ includeScrollbar: false })
const size = useElementSize(card, undefined, { box: 'border-box' })
const navbar = useElementSize(() => document.querySelector<HTMLElement>('.navbar'), undefined, {
  box: 'border-box',
})

const top = computed(() =>
  Math.min(navbar.height.value + EDGE, Math.max(0, height.value - size.height.value - EDGE))
)

const room = computed(() => ({
  x: Math.max(0, width.value - size.width.value - 2 * EDGE),
  y: Math.max(0, height.value - size.height.value - top.value - EDGE),
}))

const share = (at: number, free: number) => (free ? clamp(at / free, 0, 1) : 0)

const corner = ({ x, y }: RadioSpot): RadioSpot => ({ x: Math.round(x), y: Math.round(y) })

const moving = ref(false)
const dropped = ref(false)
let origin = { x: 0, y: 0 }
let held = false

useDraggable(card, {
  handle,
  disabled: phone,
  onStart: (_, { clientX, clientY }) => {
    origin = { x: clientX, y: clientY }
    dropped.value = false
  },
  onMove: ({ x, y }, { clientX, clientY }) => {
    if (!moving.value && Math.hypot(clientX - origin.x, clientY - origin.y) < SLOP) return
    moving.value = true
    spot.value = { x: share(x - EDGE, room.value.x), y: share(y - top.value, room.value.y) }
  },
  onEnd: () => {
    if (moving.value && spot.value) {
      spot.value = corner(spot.value)
      player.pin(spot.value)
      dropped.value = true
      held = true
      setTimeout(() => (held = false))
    }
    moving.value = false
  },
})

const hold = (event: MouseEvent) => {
  if (held) event.preventDefault()
}

const place = computed(() => {
  if (phone.value || !spot.value) return undefined
  const { x, y } = moving.value ? spot.value : corner(spot.value)
  return {
    left: `${EDGE + x * room.value.x}px`,
    top: `${top.value + y * room.value.y}px`,
    right: 'auto',
    bottom: 'auto',
  }
})

const newest = (name: string) => {
  const list = digests.value ?? []
  const show = list.find(found => found.recording.name === name)?.show
  return Boolean(show) && list.find(found => found.show === show)?.recording.name === name
}

const lifted = computed(() => route.name === 'Calculator')

const shown = computed(() => {
  const searching = Boolean(route.query.q)
  if (recording.value) {
    const { name } = recording.value
    const beside = route.name === 'Radio' && !searching && newest(name)
    return route.params.name !== name && !beside
  }
  const onPage = ON_AIR_PAGES.includes(String(route.name)) && !searching
  return !onPage || !stations.value.some(s => s.onAir && s.station === player.tuned.value)
})

const parts = computed(() => {
  const target = recording.value
  if (!target) return []
  return player.version.value === 'raw'
    ? rawParts(target, decisions.removed.value)
    : episodeParts(target)
})

const played = ({ start, end }: StripPart) =>
  clamp(((player.seconds.value - start) / (end - start)) * 100, 0, 100)

const time = computed(
  () => `\u00a0· ${position(player.seconds.value)} / ${position(player.total.value)}`
)

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
