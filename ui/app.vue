<template>
  <div class="d-flex flex-column min-vh-100">
    <NavBar />
    <main class="flex-grow-1">
      <div class="container-fluid py-4">
        <router-view></router-view>
      </div>
    </main>
    <footer class="bg-light text-center py-2">
      <small>&copy; {{ currentYear }} Portfolio Manager</small>
    </footer>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import NavBar from './components/nav-bar.vue'

const currentYear = ref(new Date().getFullYear())

onMounted(() => {
  setInterval(
    () => {
      currentYear.value = new Date().getFullYear()
    },
    1000 * 60 * 60
  )
})
</script>

<style>
body {
  background-color: #f8f9fa;
}

.container-fluid {
  max-width: 1200px;
}
</style>
