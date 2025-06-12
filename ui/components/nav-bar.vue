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
import { ref, onMounted } from 'vue'
import { getUtilityService } from '../services/service-registry'

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
const utilityService = getUtilityService()

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

<style scoped>
.navbar-scroll-container {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  -ms-overflow-style: none;
  width: 100%;
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

@media (max-width: 768px) {
  .navbar-content {
    padding-right: 10px;
    gap: 0.5rem;
  }

  .navbar-nav {
    gap: 0.5rem;
  }

  .build-info-text {
    font-size: 0.7rem;
  }

  .build-info {
    margin-left: 30px;
  }
}
</style>
