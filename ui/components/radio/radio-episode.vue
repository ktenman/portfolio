<template>
  <div :style="{ '--radio-player': `${height}px` }">
    <router-link
      :to="origin.to"
      class="mb-3 inline-flex items-center gap-1 text-sm text-ink-soft hover:text-ink"
    >
      <radio-icon name="left" />
      {{ origin.label }}
    </router-link>
    <p v-if="isLoading" class="text-ink-soft">Loading…</p>
    <p v-else-if="!recording" class="text-ink-soft">Recording {{ name }} not found.</p>
    <template v-else>
      <div class="mb-3 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 class="mb-0">{{ recording.programme }}</h2>
        <p class="mb-0 text-ink-soft">
          {{ fullDay(recording.status.startedAt) }} · Kuku · {{ span(recording) }}
        </p>
      </div>
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
import { fullDay, span } from '../../utils/radio'
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
