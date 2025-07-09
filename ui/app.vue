<template>
  <div class="d-flex flex-column min-vh-100">
    <NavBar />
    <main class="flex-grow-1">
      <div class="container-fluid py-2">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>
    <footer class="bg-light text-center py-2">
      <small>&copy; {{ currentYear }} Portfolio Manager</small>
    </footer>
    <ConfirmDialog
      v-model="confirmState.isOpen.value"
      :title="confirmState.options.value.title"
      :message="confirmState.options.value.message"
      :confirm-text="confirmState.options.value.confirmText"
      :cancel-text="confirmState.options.value.cancelText"
      :confirm-class="confirmState.options.value.confirmClass"
      @confirm="confirmState.handleConfirm"
      @cancel="confirmState.handleCancel"
    />
  </div>
</template>

<script lang="ts" setup>
import NavBar from './components/nav-bar.vue'
import ConfirmDialog from './components/shared/confirm-dialog.vue'
import { provideConfirm } from './composables/use-confirm'

const currentYear = new Date().getFullYear()
const confirmState = provideConfirm()
</script>
