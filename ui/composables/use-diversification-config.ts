import { ref, onMounted, onUnmounted } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { diversificationService } from '../services/api'
import type { CachedState } from '../components/diversification/types'

export function useDiversificationConfig(getConfig: () => CachedState) {
  const hasUnsavedChanges = ref(false)
  const saveFailed = ref(false)

  const saveToDatabase = async () => {
    try {
      await diversificationService.saveConfig(getConfig())
      saveFailed.value = false
      hasUnsavedChanges.value = false
    } catch {
      saveFailed.value = true
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
    saveFailed,
    markDirty,
  }
}
