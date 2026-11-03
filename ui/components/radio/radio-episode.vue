<template>
  <div :style="{ '--radio-player': `${height}px` }">
    <p v-if="isLoading" class="text-ink-soft">Loading…</p>
    <p v-else-if="!recording" class="text-ink-soft">Recording {{ name }} not found.</p>
    <template v-else>
      <radio-player ref="card" :recording="recording">
        <a
          :href="router.resolve(target).href"
          :aria-label="`Back to ${destination}`"
          class="relative -ml-1.25 inline-flex items-center gap-0.5 align-top font-medium text-ink-soft underline-offset-[0.2em] after:absolute after:-inset-1.5 after:rounded-container after:content-[''] hover:text-ink hover:underline focus-visible:outline-2 focus-visible:outline-brass-deep max-md:after:-inset-x-2 max-md:after:-inset-y-3"
          @click.exact.prevent="leave"
        >
          <radio-icon name="left" />
          <span v-if="query" class="flex">
            <span>Results for “</span>
            <span class="max-w-[13ch] truncate">{{ query }}</span>
            <span>”</span>
          </span>
          <template v-else>{{ page }}</template>
        </a>
        <span aria-hidden="true" class="mx-1.5">·</span>
      </radio-player>
      <radio-text :recording="recording" />
    </template>
  </div>
</template>

<script lang="ts" setup>
import { onBeforeUnmount, useTemplateRef, watch } from 'vue'
import { useElementSize, useTitle } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { edition, useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioRecording } from '../../composables/use-radio-recordings'
import { day, episodeTime } from '../../utils/radio'
import RadioIcon from './radio-icon.vue'
import RadioPlayer from './radio-player.vue'
import RadioText from './radio-text.vue'

const route = useRoute()
const router = useRouter()
const name = route.params.name as string
const { back } = router.options.history.state
const from = typeof back === 'string' ? router.resolve(back) : null
const PAGES: Record<string, string> = {
  Radio: 'Recordings',
  RadioShows: 'Shows',
  RadioCatchUp: 'Catch-up',
  'Portfolio Summary': 'Summary',
}
const known = from?.name ? from : null
const query = typeof known?.query.q === 'string' ? known.query.q : null
const page = known ? (PAGES[String(known.name)] ?? String(known.name)) : 'Recordings'
const target = known?.fullPath ?? '/radio'
const destination = query ? `results for “${query}”` : page
const leave = () => (known ? router.back() : router.push(target))
const at = Number.parseFloat(String(route.query.t))
const raw = 'raw' in route.query
const { recording, isLoading } = useRadioRecording(name)
const title = useTitle()
const player = useRadioPlayer()
const { height } = useElementSize(useTemplateRef('card'), undefined, { box: 'border-box' })

watch(
  recording,
  (value, before) => {
    if (!value) return
    title.value = `${value.programme} · ${day(value.status.startedAt)}`
    const same = player.recording.value?.name === value.name
    player.load(value, same && value.episodeBytes ? player.version.value : edition(value))
    if (before || !Number.isFinite(at)) return
    void router.replace({ query: { ...route.query, t: undefined, raw: undefined } })
    const kept =
      !raw &&
      player.version.value === 'episode' &&
      value.audible.some(k => k.start <= at && at < k.end)
    player.playFrom(kept ? episodeTime(value.audible, at) : at, kept ? 'episode' : 'raw')
  },
  { immediate: true }
)

onBeforeUnmount(player.leave)
</script>
