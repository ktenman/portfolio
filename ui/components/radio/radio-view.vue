<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3">
    <router-view v-slot="{ Component, route }">
      <radio-tabs v-if="!route.params.name">
        <component :is="Component" />
      </radio-tabs>
      <component :is="Component" v-else />
    </router-view>
  </div>
</template>

<script lang="ts" setup>
import RadioTabs from './radio-tabs.vue'
</script>
