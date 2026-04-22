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

  const resetCurrentValues = (value: number | undefined) => {
    args.allocations.value = args.allocations.value.map(a => ({ ...a, currentValue: value }))
  }

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
      resetCurrentValues(0)
    }
  }

  const applySelectionChange = async () => {
    args.onChanged()
    if (selectedPlatforms.value.length === 0) {
      resetCurrentValues(undefined)
      return
    }
    await loadCurrentValues(selectedPlatforms.value)
  }

  const togglePlatform = async (platform: string) => {
    if (selectedPlatforms.value.includes(platform)) {
      selectedPlatforms.value = selectedPlatforms.value.filter(p => p !== platform)
    } else {
      selectedPlatforms.value = [...selectedPlatforms.value, platform]
    }
    await applySelectionChange()
  }

  const toggleAllPlatforms = async () => {
    const allSelected = selectedPlatforms.value.length === args.availablePlatforms.value.length
    selectedPlatforms.value = allSelected ? [] : [...args.availablePlatforms.value]
    await applySelectionChange()
  }

  const seedFromAvailable = async (platforms: string[]) => {
    selectedPlatforms.value = [...platforms]
    await loadCurrentValues(selectedPlatforms.value)
  }

  const applyFirstTimeDefault = async () => {
    if (args.availablePlatforms.value.length > 0) {
      await seedFromAvailable(args.availablePlatforms.value)
      return
    }
    const stop = watch(args.availablePlatforms, async newPlatforms => {
      if (newPlatforms.length === 0) return
      await seedFromAvailable(newPlatforms)
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
