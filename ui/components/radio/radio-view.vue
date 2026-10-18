<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3">
    <router-view />
    <radio-dock />
  </div>
</template>

<script lang="ts" setup>
import RadioDock from './radio-dock.vue'
</script>
