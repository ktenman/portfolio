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

          <li class="nav-item build-info" v-if="buildInfo">
            <span class="text-muted build-info-text">
              {{ buildInfo.hash.substring(0, 7) }} | {{ formatDate(buildInfo.time) }}
            </span>
          </li>
        </ul>
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
    const day = date.getDate().toString().padStart(2, '0')
    const month = (date.getMonth() + 1).toString().padStart(2, '0') // Month is 0-indexed
    const year = date.getFullYear()
    return `${day}.${month}.${year}`
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
  width: 100%; /* Ensure the container takes full width */
}

.navbar-scroll-container::-webkit-scrollbar {
  display: none; /* WebKit */
}

.navbar-nav {
  display: flex;
  flex-wrap: nowrap;
  gap: 1rem;
  padding-bottom: 5px; /* Add some padding to account for the scrollbar */
  width: max-content; /* Allow the navbar to expand beyond visible area */
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

/* Style for build info */
.build-info {
  font-size: 0.75rem;
  padding: 0 10px;
  margin-left: auto; /* Push it to the right */
  display: flex;
  align-items: center;
}

.build-info-text {
  padding: 4px 8px;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .navbar-nav {
    flex-direction: row;
    justify-content: flex-start;
  }

  /* Ensure build info doesn't wrap on mobile */
  .build-info-text {
    font-size: 0.7rem;
  }
}
</style>
