<template>
  <nav class="navbar navbar-expand navbar-light bg-white border-bottom navbar-sticky">
    <div class="container-fluid">
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
            <span class="text-muted build-info-text">
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
import { utilityService } from '../services/utility-service'

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

<style lang="scss" scoped>
@use '../styles/config' as *;

.navbar-scroll-container {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  width: 100%;
  @include custom-scrollbar;

  @include media-breakpoint-down(md) {
    scrollbar-width: none;
    -ms-overflow-style: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }
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
  padding-bottom: 0.3125rem;
}

.nav-item {
  position: relative;
  white-space: nowrap;
}

.nav-link {
  position: relative;
  @include transition(color);

  &:hover {
    color: $primary-color;
  }

  &.active {
    color: $primary-color;
    font-weight: bold;
  }
}

.nav-indicator {
  position: absolute;
  bottom: -0.125rem;
  left: 0;
  width: 100%;
  height: 0.125rem;
  background-color: $primary-color;
  transform: scaleX(0);
  @include transition(transform);
}

.nav-link:hover .nav-indicator,
.nav-link.active .nav-indicator {
  transform: scaleX(1);
}

.build-info {
  font-size: 0.75rem;
  padding: 0 0.625rem;
  display: flex;
  align-items: center;
  margin-left: 1.25rem;
  white-space: nowrap;
}

.build-info-text {
  padding: 0.25rem 0.5rem;
}

.navbar-sticky {
  @include media-breakpoint-up(lg) {
    position: sticky;
    top: 0;
    z-index: 1020;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  }
}

@include media-breakpoint-down(md) {
  .navbar-content {
    padding-right: $spacing-sm * 2;
    gap: $spacing-sm;
  }

  .navbar-nav {
    gap: $spacing-sm;
  }

  .build-info-text {
    font-size: 0.7rem;
  }

  .build-info {
    margin-left: $spacing-lg * 2;
  }
}
</style>
