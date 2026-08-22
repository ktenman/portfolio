<template>
  <nav class="navbar border-b border-hairline-strong bg-surface navbar-sticky">
    <div class="mx-auto w-full max-w-app px-3">
      <div class="navbar-scroll-container">
        <div class="navbar-content">
          <ul class="navbar-nav">
            <li class="nav-item" v-for="route in routes" :key="route.path">
              <router-link class="nav-link" :to="route.path" active-class="active">
                {{ route.name }}
                <span class="nav-indicator"></span>
              </router-link>
            </li>
          </ul>
          <!-- Build info display -->
          <div class="build-info" v-if="buildInfo">
            <span class="text-body-secondary build-info-text">
              {{ buildInfo.hash.substring(0, 7) }} | {{ formatDate(buildInfo.time) }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </nav>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { utilityService } from '../services/api'

const routes = ref([
  { path: '/', name: 'Summary' },
  { path: '/calculator', name: 'Calculator' },
  { path: '/instruments', name: 'Instruments' },
  { path: '/transactions', name: 'Transactions' },
  { path: '/etf-breakdown', name: 'ETF Breakdown' },
  { path: '/diversification', name: 'Diversification' },
])

const { data: buildInfo } = useQuery({
  queryKey: ['build-info'],
  queryFn: utilityService.getBuildInfo,
  staleTime: Infinity,
  retry: false,
})

function formatDate(dateString: string): string {
  if (!dateString || dateString === 'unknown') return 'unknown'

  try {
    const date = new Date(dateString)
    const day = date.getDate().toString().padStart(2, '0')
    const month = (date.getMonth() + 1).toString().padStart(2, '0')
    const year = date.getFullYear()
    return `${day}.${month}.${year}`
  } catch (_e) {
    return dateString
  }
}
</script>

<style scoped>
.navbar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0;
}

.navbar-scroll-container {
  width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.navbar-scroll-container::-webkit-scrollbar {
  display: none;
}

.navbar-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 100%;
  width: max-content;
  padding-right: 0.9375rem;
}

.navbar-nav {
  display: flex;
  flex-wrap: nowrap;
  gap: 1rem;
  margin-bottom: 0;
  padding-left: 0;
  padding-bottom: 0.3125rem;
  list-style: none;
}

.nav-item {
  position: relative;
  white-space: nowrap;
}

.nav-link {
  display: block;
  position: relative;
  padding: 0.5rem;
  font-size: var(--text-base);
  font-weight: 500;
  letter-spacing: -0.01em;
  color: rgb(0 0 0 / 0.65);
  text-decoration: none;
  transition: color var(--transition-slow);
}

.nav-link:hover,
.nav-link.active {
  color: var(--color-signal-indigo);
}

.nav-link.active {
  font-weight: bold;
}

.nav-indicator {
  position: absolute;
  bottom: -0.125rem;
  left: 0;
  width: 100%;
  height: 0.125rem;
  background-color: var(--color-signal-indigo);
  transform: scaleX(0);
  transition: transform var(--transition-slow);
}

.nav-link:hover .nav-indicator,
.nav-link.active .nav-indicator {
  transform: scaleX(1);
}

.build-info {
  display: flex;
  align-items: center;
  margin-left: 1.25rem;
  padding: 0 0.625rem;
  font-size: var(--text-label);
  white-space: nowrap;
}

.build-info-text {
  padding: 0.25rem 0.5rem;
}

@media (min-width: 992px) {
  .navbar-sticky {
    position: sticky;
    top: 0;
    z-index: var(--z-sticky);
    box-shadow: var(--shadow-nav);
  }
}

@media (max-width: 767.98px) {
  .navbar-content {
    gap: 0.5rem;
    padding-right: 1rem;
  }

  .navbar-nav {
    gap: 0.5rem;
  }

  .build-info {
    margin-left: 3rem;
  }
}
</style>
