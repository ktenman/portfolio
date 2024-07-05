<template>
  <nav class="navbar navbar-expand-lg navbar-light bg-light">
    <div class="container-fluid">
      <button
        class="navbar-toggler"
        type="button"
        data-bs-toggle="collapse"
        data-bs-target="#navbarNav"
        aria-controls="navbarNav"
        aria-expanded="false"
        aria-label="Toggle navigation"
      >
        <span class="navbar-toggler-icon"></span>
      </button>
      <div class="collapse navbar-collapse" id="navbarNav">
        <ul class="navbar-nav">
          <li class="nav-item" v-for="route in routes" :key="route.path">
            <router-link class="nav-link" :to="route.path" active-class="active">
              {{ route.name }}
              <span class="nav-indicator"></span>
            </router-link>
          </li>
        </ul>
      </div>
    </div>
  </nav>
</template>

<script lang="ts" setup>
import { ref } from 'vue'

const routes = ref([
  { path: '/', name: 'Portfolio Summary' },
  { path: '/transactions', name: 'Transactions' },
  { path: '/instruments', name: 'Instruments' },
])
</script>

<style scoped>
.navbar-nav {
  display: flex;
  gap: 1rem;
}

.nav-item {
  position: relative;
}

.nav-link {
  position: relative;
  transition: color 0.3s ease;
}

.nav-link:hover {
  color: #007bff;
}

.nav-indicator {
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 100%;
  height: 2px;
  background-color: #007bff;
  transform: scaleX(0);
  transition: transform 0.3s ease;
}

.nav-link:hover .nav-indicator,
.nav-link.active .nav-indicator {
  transform: scaleX(1);
}

.nav-link.active {
  color: #007bff;
  font-weight: bold;
}
</style>
