import { type Ref, watch } from 'vue'
import { useLocalStorage } from '@vueuse/core'

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

  const isPlatformSelected = (platform: string): boolean =>
    selectedPlatforms.value.includes(platform)

  const togglePlatform = (platform: string) => {
    const index = selectedPlatforms.value.indexOf(platform)
    if (index > -1) {
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

  return { selectedPlatforms, isPlatformSelected, togglePlatform, toggleAllPlatforms }
}
