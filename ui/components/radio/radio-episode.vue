<template>
  <div>
    <router-link
      to="/radio"
      class="mb-3 inline-flex items-center gap-1 text-sm text-ink-soft hover:text-ink"
    >
      <radio-icon name="left" />
      Saated
    </router-link>
    <p v-if="isLoading" class="text-ink-soft">Laadin…</p>
    <p v-else-if="!recording" class="text-ink-soft">Salvestust {{ name }} ei leitud.</p>
    <template v-else>
      <div class="mb-3 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 class="mb-0">{{ recording.programme }}</h2>
        <p class="mb-0 text-ink-soft">
          {{ fullDay(recording.status.startedAt) }} · Kuku · {{ span(recording) }}
        </p>
      </div>
      <nav class="mb-4 flex gap-2" aria-label="Saate vaated">
        <router-link
          v-for="tab in TABS"
          :key="tab.path"
          :to="`/radio/${name}${tab.path}`"
          class="platform-btn"
          exact-active-class="active"
        >
          {{ tab.label }}
        </router-link>
      </nav>
      <router-view :recording="recording" />
    </template>
  </div>
</template>

<script lang="ts" setup>
import { watch } from 'vue'
import { useRoute } from 'vue-router'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioRecording } from '../../composables/use-radio-recordings'
import { fullDay, span } from '../../utils/radio'
import RadioIcon from './radio-icon.vue'

const TABS = [
  { path: '', label: 'Kuula' },
  { path: '/cuts', label: 'Lõiked' },
  { path: '/text', label: 'Tekst' },
]

const route = useRoute()
const name = route.params.name as string
const { recording, isLoading } = useRadioRecording(name)
const player = useRadioPlayer()

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
