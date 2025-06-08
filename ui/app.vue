<template>
  <div class="d-flex flex-column min-vh-100">
    <NavBar />
    <main class="flex-grow-1">
      <div class="container-fluid py-2">
        <router-view></router-view>
      </div>
    </main>
    <footer class="bg-light text-center py-2">
      <small>&copy; {{ currentYear }} Portfolio Manager</small>
    </footer>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import NavBar from './components/nav-bar.vue'
import { APP_CONFIG } from './constants/app-config'

const currentYear = ref(new Date().getFullYear())

onMounted(() => {
  const now = new Date()
  const nextYear = new Date(now.getFullYear() + 1, 0, 1)
  const msUntilNextYear = nextYear.getTime() - now.getTime()

  setTimeout(() => {
    currentYear.value = new Date().getFullYear()

    setInterval(() => {
      currentYear.value = new Date().getFullYear()
    }, APP_CONFIG.YEAR_UPDATE_INTERVAL_MS)
  }, msUntilNextYear)
})
</script>

<style>
@import './styles/common.css';

body {
  background-color: #f8f9fa;
}

.container-fluid {
  max-width: 1200px;
}
</style>
