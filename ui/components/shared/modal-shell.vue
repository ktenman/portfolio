<template>
  <dialog
    ref="dialogEl"
    tabindex="-1"
    :id="modalId"
    class="modal"
    :aria-labelledby="`${modalId}Label`"
    @click.self="requestClose"
    @cancel="onCancel"
    @close="onClose"
  >
    <div class="modal-dialog" :class="dialogClasses">
      <div class="modal-content" autofocus tabindex="-1" @click.stop>
        <div class="modal-header">
          <h5 class="modal-title" :id="`${modalId}Label`">{{ title }}</h5>
          <close-button @click="requestClose" />
        </div>
        <div class="modal-body">
          <slot />
        </div>
        <div class="modal-footer">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import CloseButton from './close-button.vue'

interface Props {
  open: boolean
  modalId: string
  title?: string
  size?: 'lg'
  centered?: boolean
  closeOnEsc?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  size: undefined,
  centered: false,
  closeOnEsc: true,
})

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const dialogEl = ref<HTMLDialogElement | null>(null)

const dialogClasses = computed(() => ({
  'modal-lg': props.size === 'lg',
  'modal-dialog-centered': props.centered,
}))

const lockScroll = () => {
  const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth
  document.body.style.overflow = 'hidden'
  if (scrollbarWidth > 0) document.body.style.paddingRight = `${scrollbarWidth}px`
}

const unlockScroll = () => {
  document.body.style.overflow = ''
  document.body.style.paddingRight = ''
}

const requestClose = () => {
  dialogEl.value?.close()
}

const onCancel = (event: Event) => {
  if (!props.closeOnEsc) event.preventDefault()
}

const onClose = () => {
  unlockScroll()
  if (props.open) emit('update:open', false)
}

const sync = (isOpen: boolean) => {
  const dialog = dialogEl.value
  if (!dialog) return
  if (isOpen && !dialog.open) {
    dialog.showModal()
    lockScroll()
    return
  }
  if (!isOpen && dialog.open) dialog.close()
}

watch(() => props.open, sync, { flush: 'post' })

onMounted(() => sync(props.open))

onBeforeUnmount(() => {
  unlockScroll()
})
</script>

<style scoped>
dialog.modal {
  display: none;
  max-width: none;
  max-height: none;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
}

dialog.modal[open] {
  display: block;
}

dialog.modal::backdrop {
  background-color: rgba(0, 0, 0, 0.5);
}

.modal {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow-y: auto;
}

.modal-dialog {
  position: relative;
  width: auto;
  margin: 0.5rem;
  pointer-events: none;
}

.modal-dialog-centered {
  display: flex;
  align-items: center;
  min-height: calc(100% - 1rem);
}

.modal-content {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  pointer-events: auto;
  background-color: var(--color-gray-100);
  background-clip: padding-box;
  border: 1px solid rgb(0 0 0 / 0.175);
  border-radius: var(--radius-container);
  outline: 0;
}

.modal-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid var(--color-hairline-strong);
}

.modal-header .btn-close {
  padding: 0.5rem;
  margin: -0.5rem -0.5rem -0.5rem auto;
}

.modal-title {
  margin-bottom: 0;
  line-height: 1.5;
}

.modal-body {
  position: relative;
  flex: 1 1 auto;
  padding: 1rem;
}

.modal-footer {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  padding: 1rem;
  border-top: 1px solid var(--color-hairline-strong);
}

@media (min-width: 576px) {
  .modal-dialog {
    max-width: 500px;
    margin: 1.75rem auto;
  }

  .modal-dialog-centered {
    min-height: calc(100% - 3.5rem);
  }
}

@media (min-width: 992px) {
  .modal-lg {
    max-width: 800px;
  }
}
</style>
