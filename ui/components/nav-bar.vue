<template>
  <nav class="navbar navbar-expand navbar-light bg-light">
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
import { onMounted, ref } from 'vue'
import { utilityService } from '../services'

interface BuildInfo {
  hash: string
  time: string
}

const routes = ref([
  { path: '/', name: 'Summary' },
  { path: '/calculator', name: 'Calculator' },
  { path: '/instruments', name: 'Instruments' },
  { path: '/transactions', name: 'Transactions' },
])

const buildInfo = ref<BuildInfo | null>(null)

onMounted(async () => {
  try {
    buildInfo.value = await utilityService.getBuildInfo()
  } catch (error) {
    console.error('Error fetching build info:', error)
  }
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
@import '../styles/variables';
@import '../styles/mixins';

.navbar-scroll-container {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  width: 100%;
  @include scrollbar-style;
}

.navbar-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 100%;
  width: max-content;
  padding-right: 15px;
}

.navbar-nav {
  display: flex;
  flex-wrap: nowrap;
  gap: 1rem;
  padding-bottom: 5px;
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
  bottom: -2px;
  left: 0;
  width: 100%;
  height: 2px;
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
  padding: 0 10px;
  display: flex;
  align-items: center;
  margin-left: 20px;
  white-space: nowrap;
}

.build-info-text {
  padding: 4px 8px;
}

@include responsive(md) {
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
