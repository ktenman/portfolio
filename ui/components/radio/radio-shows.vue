<template>
  <div class="grid gap-8 lg:grid-cols-[minmax(0,1fr)_20rem]">
    <div class="min-w-0">
      <radio-programmes />
      <radio-on-air class="lg:hidden" />
    </div>
    <radio-station />
  </div>
</template>

<script lang="ts" setup>
import RadioOnAir from './radio-on-air.vue'
import RadioProgrammes from './radio-programmes.vue'
import RadioStation from './radio-station.vue'
</script>
