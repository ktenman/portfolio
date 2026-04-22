import { ref, watch, type Ref } from 'vue'
import { instrumentsService } from '../services/instruments-service'
import type { AllocationInput } from '../components/diversification/types'

interface UseDiversificationPlatformsArgs {
  allocations: Ref<AllocationInput[]>
  availablePlatforms: Ref<string[]>
  onChanged: () => void
}

export function useDiversificationPlatforms(args: UseDiversificationPlatformsArgs) {
  const selectedPlatforms = ref<string[]>([])

  const loadCurrentValues = async (platforms: string[]) => {
    try {
      const response = await instrumentsService.getAll(platforms)
      const valueMap = new Map(
        response.instruments
          .filter((i): i is typeof i & { id: number } => i.id !== null)
          .map(i => [i.id, i.currentValue ?? 0])
      )
      args.allocations.value = args.allocations.value.map(a => ({
        ...a,
        currentValue: valueMap.get(a.instrumentId) ?? 0,
      }))
    } catch {
      args.allocations.value = args.allocations.value.map(a => ({ ...a, currentValue: 0 }))
    }
  }

  const applySelectionChange = async () => {
    args.onChanged()
    if (selectedPlatforms.value.length === 0) {
      args.allocations.value = args.allocations.value.map(a => ({
        ...a,
        currentValue: undefined,
      }))
      return
    }
    await loadCurrentValues(selectedPlatforms.value)
  }

  const togglePlatform = async (platform: string) => {
    const idx = selectedPlatforms.value.indexOf(platform)
    selectedPlatforms.value =
      idx > -1
        ? selectedPlatforms.value.filter(p => p !== platform)
        : [...selectedPlatforms.value, platform]
    await applySelectionChange()
  }

  const toggleAllPlatforms = async () => {
    selectedPlatforms.value =
      selectedPlatforms.value.length === args.availablePlatforms.value.length
        ? []
        : [...args.availablePlatforms.value]
    await applySelectionChange()
  }

  const applyFirstTimeDefault = async () => {
    if (args.availablePlatforms.value.length > 0) {
      selectedPlatforms.value = [...args.availablePlatforms.value]
      await loadCurrentValues(selectedPlatforms.value)
      return
    }
    const stop = watch(args.availablePlatforms, async newPlatforms => {
      if (newPlatforms.length === 0) return
      selectedPlatforms.value = [...newPlatforms]
      await loadCurrentValues(selectedPlatforms.value)
      stop()
    })
  }

  return {
    selectedPlatforms,
    togglePlatform,
    toggleAllPlatforms,
    loadCurrentValues,
    applyFirstTimeDefault,
  }
}
