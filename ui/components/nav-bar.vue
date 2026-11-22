<template>
  <nav class="navbar border-b border-hairline-strong bg-surface navbar-sticky">
    <div class="navbar-row mx-auto w-full max-w-app px-3">
      <div ref="row" class="navbar-scroll-container">
        <div class="navbar-content">
          <ul class="navbar-nav">
            <li class="nav-item" v-for="route in routes" :key="route.path">
              <router-link class="nav-link" :to="route.path" active-class="active">
                {{ route.name }}
                <span class="nav-indicator"></span>
              </router-link>
            </li>
          </ul>
          <span v-if="buildInfo" class="text-body-secondary build-info">
            {{ buildInfo.hash.substring(0, 7) }} {{ formatDate(buildInfo.time) }}
          </span>
        </div>
      </div>
      <div class="nav-end">
        <button
          class="nav-glyph theme-toggle"
          type="button"
          popovertarget="theme-menu"
          aria-label="Theme"
          title="Theme"
        >
          <radio-icon :name="theme" />
        </button>
        <div id="theme-menu" class="theme-menu" popover role="group" aria-label="Theme">
          <button
            v-for="option in themes"
            :key="option.value"
            class="theme-option"
            type="button"
            popovertarget="theme-menu"
            popovertargetaction="hide"
            :aria-pressed="theme === option.value"
            @click="choose(option.value)"
          >
            <radio-icon :name="option.value" class="text-body-secondary" />
            {{ option.label }}
            <radio-icon v-if="theme === option.value" name="check" class="ml-auto text-brass" />
          </button>
        </div>
        <form v-if="hosted && buildInfo" method="post" action="/logout">
          <button class="nav-glyph" type="submit" aria-label="Log out" title="Log out">
            <radio-icon name="logout" />
          </button>
        </form>
      </div>
    </div>
  </nav>
</template>

<script lang="ts" setup>
import { nextTick, ref, useTemplateRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { useTheme, type ThemeChoice } from '../composables/use-theme'
import { utilityService } from '../services/api'
import { revealInRow } from '../utils/dom'
import RadioIcon from './radio/radio-icon.vue'

const routes = ref([
  { path: '/', name: 'Summary' },
  { path: '/radio', name: 'Radio' },
  { path: '/poff', name: 'PÖFF' },
  { path: '/instruments', name: 'Instruments' },
  { path: '/transactions', name: 'Transactions' },
  { path: '/etf-breakdown', name: 'ETF Breakdown' },
  { path: '/diversification', name: 'Diversification' },
  { path: '/calculator', name: 'Calculator' },
  { path: '/monitoring', name: 'Monitoring' },
])

const themes: { value: ThemeChoice; label: string }[] = [
  { value: 'auto', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

const hosted = window.location.hostname === 'fov.ee'
const { theme, choose } = useTheme()
const row = useTemplateRef<HTMLDivElement>('row')
const route = useRoute()

watch(
  () => route.path,
  async () => {
    await nextTick()
    await document.fonts.ready
    revealInRow(row.value, '.nav-link.active', 'nearest')
  },
  { immediate: true }
)

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

.navbar-row {
  display: flex;
  align-items: center;
}

.navbar-scroll-container {
  flex: 1 1 auto;
  min-width: 0;
  margin-block: -0.25rem;
  padding-block: 0.25rem;
  overflow-x: auto;
  overflow-y: hidden;
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
  color: var(--color-nav-link);
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

.nav-end {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  padding-inline: 0.375rem;
  font-size: var(--text-label);
  white-space: nowrap;
}

.build-info {
  margin-left: 1.875rem;
  font-size: var(--text-label);
  white-space: nowrap;
  word-spacing: 0.25em;
}

.nav-glyph {
  display: grid;
  place-items: center;
  width: 1.75rem;
  height: 1.75rem;
  color: var(--color-body-secondary);
  cursor: pointer;
  border-radius: var(--radius-control);
  transition:
    color var(--transition-fast),
    background-color var(--transition-fast);
}

.nav-glyph:hover,
.theme-toggle:has(+ :popover-open) {
  color: var(--color-signal-indigo);
  background-color: var(--color-surface-hover);
}

.theme-menu {
  width: 9.25rem;
  margin: auto;
  padding: 0.25rem;
  font-size: var(--text-2xs);
  color: var(--color-ink);
  background: var(--color-surface);
  border: 1px solid var(--color-hairline-strong);
  border-radius: var(--radius-container);
  box-shadow: var(--shadow-overlay);
}

@supports (anchor-name: --theme) {
  .theme-toggle {
    anchor-name: --theme;
  }

  .theme-menu {
    position-anchor: --theme;
    position-try-fallbacks: flip-inline;
    inset: auto;
    top: calc(anchor(bottom) + 0.375rem);
    right: anchor(right);
    margin: 0;
  }
}

.theme-option {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  height: 2rem;
  padding: 0 0.5rem;
  cursor: pointer;
  border-radius: var(--radius-control);
}

.theme-option:hover {
  background-color: var(--color-surface-hover);
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
  }

  .navbar-nav {
    gap: 0.5rem;
  }

  .build-info {
    margin-left: 3.625rem;
  }

  .nav-glyph {
    width: 2.75rem;
    height: 2.75rem;
  }

  .theme-option {
    height: 2.75rem;
  }
}
</style>
