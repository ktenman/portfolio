<template>
  <div :style="{ '--radio-player': `${height}px` }">
    <p v-if="isLoading" class="text-ink-soft">Loading…</p>
    <p v-else-if="!recording" class="text-ink-soft">Recording {{ name }} not found.</p>
    <template v-else>
      <radio-player ref="card" :recording="recording">
        <router-link
          :to="origin.to"
          :aria-label="`Back to ${origin.label}`"
          :title="`Back to ${origin.label}`"
          class="relative -ml-2.5 grid self-center rounded-full text-ink-soft after:absolute after:-inset-1.5 after:content-[''] hover:text-ink focus-visible:outline-2 focus-visible:outline-brass-deep [&>svg]:size-8"
        >
          <radio-icon name="left" />
        </router-link>
      </radio-player>
      <radio-text :recording="recording" />
    </template>
  </div>
</template>

<script lang="ts" setup>
import { useTemplateRef, watch } from 'vue'
import { useElementSize, useTitle } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { useRadioPlayer } from '../../composables/use-radio-player'
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
const origin = from?.query.q
  ? { to: from.fullPath, label: 'search results' }
  : from?.name === 'RadioCatchUp'
    ? { to: from.fullPath, label: 'Catch-up' }
    : { to: '/radio', label: 'Recordings' }
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
    const start = value.editList && value.episodeBytes ? 'episode' : 'raw'
    player.load(value, same && value.episodeBytes ? player.version.value : start)
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
</script>
