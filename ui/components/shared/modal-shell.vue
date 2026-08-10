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
          <button type="button" class="btn-close" aria-label="Close" @click="requestClose"></button>
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
</style>
