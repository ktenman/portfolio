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
import { useElementSize } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioRecording } from '../../composables/use-radio-recordings'
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
