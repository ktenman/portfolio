<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3" lang="et">
    <audio ref="audio" preload="metadata" />
    <router-view />
  </div>
</template>

<script lang="ts" setup>
import { onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import { useRadioPlayer } from '../../composables/use-radio-player'

const audio = useTemplateRef<HTMLAudioElement>('audio')
const player = useRadioPlayer()

onMounted(() => audio.value && player.attach(audio.value))
onBeforeUnmount(player.detach)
</script>
