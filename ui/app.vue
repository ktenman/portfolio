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
import NavBar from './components/nav-bar.vue'

const currentYear = new Date().getFullYear()
</script>
