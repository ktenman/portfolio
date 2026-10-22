<template>
  <div :style="{ '--radio-player': `${height}px` }">
    <div class="mb-3 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
      <div class="flex items-baseline">
        <router-link
          :to="origin.to"
          :aria-label="`Back to ${origin.label}`"
          :title="`Back to ${origin.label}`"
          class="relative -ml-2.5 grid self-center rounded-full text-ink-soft after:absolute after:-inset-1.5 after:content-[''] hover:text-ink focus-visible:outline-2 focus-visible:outline-brass-deep [&>svg]:size-8"
        >
          <radio-icon name="left" />
        </router-link>
        <h2 v-if="recording" class="mb-0">{{ recording.programme }}</h2>
      </div>
      <p v-if="recording" class="mb-0 text-ink-soft">{{ fullDay(recording.status.startedAt) }}</p>
    </div>
    <p v-if="isLoading" class="text-ink-soft">Loading…</p>
    <p v-else-if="!recording" class="text-ink-soft">Recording {{ name }} not found.</p>
    <template v-else>
      <radio-player ref="card" :recording="recording" />
      <radio-text :recording="recording" />
    </template>
  </div>
</template>

<script lang="ts" setup>
import { useTemplateRef, watch } from 'vue'
import { useElementSize } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioRecording } from '../../composables/use-radio-recordings'
import { fullDay } from '../../utils/radio'
import RadioIcon from './radio-icon.vue'
import RadioPlayer from './radio-player.vue'
import RadioText from './radio-text.vue'

const route = useRoute()
const router = useRouter()
const name = route.params.name as string
const { back } = router.options.history.state
const origin =
  typeof back === 'string' && router.resolve(back).name === 'RadioCatchUp'
    ? { to: back, label: 'Catch-up' }
    : { to: '/radio', label: 'Shows' }
const { recording, isLoading } = useRadioRecording(name)
const player = useRadioPlayer()
const { height } = useElementSize(useTemplateRef('card'), undefined, { box: 'border-box' })

watch(
  recording,
  value => {
    if (!value) return
    const same = player.recording.value?.name === value.name
    const start = value.editList && value.episodeBytes ? 'episode' : 'raw'
    player.load(value, same && value.episodeBytes ? player.version.value : start)
  },
  { immediate: true }
)
</script>
