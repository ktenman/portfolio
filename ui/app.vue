<template>
  <div v-if="isAuthChecking" class="auth-loading">
    <div class="auth-spinner"></div>
  </div>
  <div v-else class="flex min-h-screen flex-col">
    <NavBar />
    <main class="grow">
      <div class="mx-auto w-full max-w-app px-3 py-2">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>
    <footer class="bg-gray-100 py-2 text-center">
      <small>&copy; {{ currentYear }} Portfolio Manager</small>
    </footer>
    <RadioMiniPlayer v-if="player.started.value" />
    <ConfirmDialog
      v-model="confirmState.isOpen.value"
      :title="confirmState.options.value.title"
      :message="confirmState.options.value.message"
      :confirm-text="confirmState.options.value.confirmText"
      :cancel-text="confirmState.options.value.cancelText"
      :variant="confirmState.options.value.variant"
      @confirm="confirmState.handleConfirm"
      @cancel="confirmState.handleCancel"
    />
    <ToastContainer />
  </div>
  <audio ref="audio" preload="metadata" />
</template>

<script lang="ts" setup>
import { defineAsyncComponent, onMounted, useTemplateRef } from 'vue'
import NavBar from './components/nav-bar.vue'
import ToastContainer from './components/shared/toast-container.vue'
import { provideConfirm } from './composables/use-confirm'
import { useAuthState } from './composables/use-auth-state'
import { useEnumValues } from './composables/use-enum-values'
import { useLiveUpdates } from './composables/use-live-updates'
import { useRadioPlayer } from './composables/use-radio-player'

const ConfirmDialog = defineAsyncComponent(() => import('./components/shared/confirm-dialog.vue'))
const RadioMiniPlayer = defineAsyncComponent(
  () => import('./components/radio/radio-mini-player.vue')
)

const currentYear = new Date().getFullYear()
const confirmState = provideConfirm()
const { isAuthChecking, checkAuth } = useAuthState()
const { loadAll } = useEnumValues()
const audio = useTemplateRef<HTMLAudioElement>('audio')
const player = useRadioPlayer()
useLiveUpdates()

onMounted(() => {
  checkAuth()
  loadAll()
  if (audio.value) player.attach(audio.value)
})
</script>

<style scoped>
.auth-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: var(--color-gray-100);
}
.auth-spinner {
  width: 3rem;
  height: 3rem;
  border: 0.25rem solid var(--color-gray-200);
  border-top-color: var(--color-signal-indigo);
  border-radius: 50%;
  animation: auth-spin 1s linear infinite;
}
@keyframes auth-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
