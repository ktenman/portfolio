<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3">
    <audio ref="audio" preload="metadata" />
    <router-view v-slot="{ Component, route }">
      <radio-tabs v-if="route.name === 'Radio' || route.name === 'RadioCatchUp'">
        <component :is="Component" />
      </radio-tabs>
      <component :is="Component" v-else />
    </router-view>
  </div>
</template>

<script lang="ts" setup>
import { onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioTabs from './radio-tabs.vue'

const audio = useTemplateRef<HTMLAudioElement>('audio')
const player = useRadioPlayer()

onMounted(() => audio.value && player.attach(audio.value))
onBeforeUnmount(player.detach)
</script>
