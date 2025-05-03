<template>
  <nav class="navbar navbar-expand navbar-light bg-light">
    <div class="container-fluid">
      <div class="navbar-scroll-container">
        <ul class="navbar-nav">
          <li class="nav-item" v-for="route in routes" :key="route.path">
            <router-link class="nav-link" :to="route.path" active-class="active">
              {{ route.name }}
              <span class="nav-indicator"></span>
            </router-link>
          </li>
        </ul>
      </div>

      <div class="build-info ms-auto" v-if="buildInfo">
        <small class="text-muted">
          {{ buildInfo.hash.substring(0, 7) }} | {{ formatDate(buildInfo.time) }}
        </small>
      </div>
    </div>
  </nav>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import { BuildInfoService, BuildInfo } from '../services/build-info-service'

const routes = ref([
  { path: '/', name: 'Summary' },
  { path: '/calculator', name: 'Calculator' },
  { path: '/instruments', name: 'Instruments' },
  { path: '/transactions', name: 'Transactions' },
])

const buildInfo = ref<BuildInfo | null>(null)
const buildInfoService = new BuildInfoService()

onMounted(async () => {
  try {
    buildInfo.value = await buildInfoService.getBuildInfo()
  } catch (error) {
    console.error('Error fetching build info:', error)
  }
})

function formatDate(dateString: string): string {
  if (!dateString || dateString === 'unknown') return 'unknown'

  try {
    const date = new Date(dateString)
    return date.toLocaleDateString()
  } catch (e) {
    return dateString
  }
}
</script>

<style scoped>
.navbar-scroll-container {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* Internet Explorer 10+ */
}

.navbar-scroll-container::-webkit-scrollbar {
  display: none; /* WebKit */
}

.navbar-nav {
  display: flex;
  flex-wrap: nowrap;
  gap: 1rem;
  padding-bottom: 5px; /* Add some padding to account for the scrollbar */
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

@media (max-width: 768px) {
  .navbar-nav {
    flex-direction: row;
    justify-content: flex-start;
  }
}
</style>
