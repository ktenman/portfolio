import { computed, watch, type ComputedRef, type Ref } from 'vue'
import { useColorMode } from '@vueuse/core'
import { STORAGE_KEYS } from '../constants'
import type { Theme } from '../models/generated/domain-models'
import { preferencesService } from '../services/api'

export type ThemeChoice = 'auto' | 'light' | 'dark'

const CHOICES: readonly string[] = ['auto', 'light', 'dark']

const mode = useColorMode({ attribute: 'data-theme', storageKey: STORAGE_KEYS.THEME })

watch(
  mode.store,
  choice => {
    if (!CHOICES.includes(choice)) mode.store.value = 'auto'
  },
  { immediate: true }
)

const dark = computed(() => mode.state.value === 'dark')

let chosen = false
let saving = Promise.resolve()

const choose = (next: ThemeChoice) => {
  chosen = true
  mode.store.value = next
  saving = saving
    .then(() => preferencesService.save(next.toUpperCase() as Theme))
    .catch(() => undefined)
}

const recall = async () => {
  const saved = await preferencesService.get().catch(() => null)
  if (!saved || chosen) return
  mode.store.value = saved.theme.toLowerCase() as ThemeChoice
}

export function useTheme(): {
  theme: Ref<ThemeChoice>
  dark: ComputedRef<boolean>
  choose: (next: ThemeChoice) => void
  recall: () => Promise<void>
} {
  return { theme: mode.store, dark, choose, recall }
}
