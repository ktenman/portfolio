import { computed, type Ref, watch } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { getFilterParam } from '../services/etf-chart-service'

export function usePlatformFilter(storageKey: string, availablePlatforms: Ref<string[]>) {
  const selectedPlatforms = useLocalStorage<string[]>(storageKey, [])

  watch(
    availablePlatforms,
    newPlatforms => {
      if (newPlatforms.length === 0) return
      if (selectedPlatforms.value.length === 0) {
        selectedPlatforms.value = [...newPlatforms]
        return
      }
      const validPlatforms = selectedPlatforms.value.filter(p => newPlatforms.includes(p))
      if (validPlatforms.length === 0) {
        selectedPlatforms.value = [...newPlatforms]
      } else if (validPlatforms.length !== selectedPlatforms.value.length) {
        selectedPlatforms.value = validPlatforms
      }
    },
    { immediate: true }
  )

  const togglePlatform = (platform: string) => {
    if (selectedPlatforms.value.includes(platform)) {
      selectedPlatforms.value = selectedPlatforms.value.filter(p => p !== platform)
    } else {
      selectedPlatforms.value = [...selectedPlatforms.value, platform]
    }
  }

  const toggleAllPlatforms = () => {
    if (selectedPlatforms.value.length === availablePlatforms.value.length) {
      selectedPlatforms.value = []
    } else {
      selectedPlatforms.value = [...availablePlatforms.value]
    }
  }

  const activePlatforms = computed(() =>
    getFilterParam(selectedPlatforms.value, availablePlatforms.value)
  )

  return { selectedPlatforms, activePlatforms, togglePlatform, toggleAllPlatforms }
}
