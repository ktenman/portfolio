import { ref, onMounted, onUnmounted } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { diversificationService } from '../services/diversification-service'
import type { CachedState } from '../components/diversification/types'

type SaveStatus = 'idle' | 'saving' | 'saved' | 'error'

export function useDiversificationConfig(getConfig: () => CachedState) {
  const hasUnsavedChanges = ref(false)
  const saveStatus = ref<SaveStatus>('idle')

  const saveToDatabase = async () => {
    saveStatus.value = 'saving'
    try {
      await diversificationService.saveConfig(getConfig())
      saveStatus.value = 'saved'
      hasUnsavedChanges.value = false
      setTimeout(() => {
        if (saveStatus.value === 'saved') saveStatus.value = 'idle'
      }, 2000)
    } catch {
      saveStatus.value = 'error'
    }
  }

  const debouncedSave = useDebounceFn(saveToDatabase, 1000)

  const markDirty = () => {
    hasUnsavedChanges.value = true
    debouncedSave()
  }

  const handleBeforeUnload = (e: BeforeUnloadEvent) => {
    if (hasUnsavedChanges.value) {
      e.preventDefault()
      e.returnValue = ''
    }
  }

  onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload))
  onUnmounted(() => window.removeEventListener('beforeunload', handleBeforeUnload))

  return {
    hasUnsavedChanges,
    saveStatus,
    debouncedSave,
    markDirty,
  }
}
