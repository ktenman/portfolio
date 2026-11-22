/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { STORAGE_KEYS } from '../constants'
import { Theme, type UserPreferenceDto } from '../models/generated/domain-models'
import { deferred } from '../tests/test-utils'

const saved = vi.hoisted(() => ({ get: vi.fn(), save: vi.fn() }))

vi.mock('../services/api', () => ({
  preferencesService: {
    get: async () => saved.get(),
    save: async (theme: Theme) => saved.save(theme),
  },
}))

const loadTheme = async (prefersDark: boolean) => {
  vi.resetModules()
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: prefersDark && query === '(prefers-color-scheme: dark)',
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }))
  const { useTheme } = await import('./use-theme')
  return useTheme()
}

describe('useTheme', () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
    vi.resetAllMocks()
    localStorage.clear()
    window.dispatchEvent(new StorageEvent('storage', { storageArea: localStorage }))
    delete document.documentElement.dataset.theme
  })

  it('should choose auto and follow a dark device when nothing is stored', async () => {
    const { theme, dark } = await loadTheme(true)

    expect([theme.value, dark.value]).toEqual(['auto', true])
  })

  it('should choose auto and follow a light device when nothing is stored', async () => {
    const { theme, dark } = await loadTheme(false)

    expect([theme.value, dark.value]).toEqual(['auto', false])
  })

  it('should go dark on a light device when dark is stored', async () => {
    localStorage.setItem(STORAGE_KEYS.THEME, 'dark')

    const { dark } = await loadTheme(false)

    expect(dark.value).toBe(true)
  })

  it('should treat a stored sepia as auto and store auto in its place', async () => {
    localStorage.setItem(STORAGE_KEYS.THEME, 'sepia')

    const { theme, dark } = await loadTheme(true)
    await nextTick()

    expect([
      theme.value,
      dark.value,
      localStorage.getItem(STORAGE_KEYS.THEME),
      document.documentElement.dataset.theme,
    ]).toEqual(['auto', true, 'auto', 'dark'])
  })

  it('should set the chosen theme and save it', async () => {
    const { theme, choose } = await loadTheme(false)

    choose('dark')
    await flushPromises()

    expect([theme.value, saved.save.mock.calls]).toEqual(['dark', [[Theme.DARK]]])
  })

  it('should save a second choice only after the first save settles', async () => {
    const first = deferred<void>()
    saved.save.mockReturnValueOnce(first.promise)
    const { choose } = await loadTheme(false)

    choose('light')
    choose('dark')
    await flushPromises()
    const pending = [...saved.save.mock.calls]
    first.resolve()
    await flushPromises()

    expect([pending, saved.save.mock.calls]).toEqual([
      [[Theme.LIGHT]],
      [[Theme.LIGHT], [Theme.DARK]],
    ])
  })

  it('should save a second choice after the first save fails', async () => {
    const first = deferred<void>()
    saved.save.mockReturnValueOnce(first.promise)
    const { choose } = await loadTheme(false)

    choose('light')
    choose('dark')
    first.reject(new Error('Serveri viga'))
    await flushPromises()

    expect(saved.save.mock.calls).toEqual([[Theme.LIGHT], [Theme.DARK]])
  })

  it('should keep the chosen theme when saving it fails', async () => {
    saved.save.mockRejectedValue(new Error('Serveri viga'))
    const { theme, choose } = await loadTheme(false)

    choose('dark')
    await flushPromises()

    expect(theme.value).toBe('dark')
  })

  it('should apply a saved dark theme over a local auto', async () => {
    saved.get.mockResolvedValue({ theme: Theme.DARK })
    const { theme, recall } = await loadTheme(false)

    await recall()

    expect([theme.value, localStorage.getItem(STORAGE_KEYS.THEME)]).toEqual(['dark', 'dark'])
  })

  it('should not save a theme it recalls', async () => {
    saved.get.mockResolvedValue({ theme: Theme.LIGHT })
    const { recall } = await loadTheme(true)

    await recall()

    expect(saved.save).not.toHaveBeenCalled()
  })

  it('should keep the local theme when the saved one cannot be read', async () => {
    localStorage.setItem(STORAGE_KEYS.THEME, 'light')
    saved.get.mockRejectedValue(new Error('Serveri viga'))
    const { theme, recall } = await loadTheme(true)

    await recall()

    expect(theme.value).toBe('light')
  })

  it('should keep a theme chosen before the saved one arrives', async () => {
    const answer = deferred<UserPreferenceDto>()
    saved.get.mockReturnValue(answer.promise)
    const { theme, choose, recall } = await loadTheme(false)

    const recalled = recall()
    choose('light')
    answer.resolve({ theme: Theme.DARK })
    await recalled

    expect(theme.value).toBe('light')
  })

  it('should read the stored theme in the page before the app loads', () => {
    const page = readFileSync(join(import.meta.dirname, '..', 'index.html'), 'utf8')

    expect(page).toContain(`localStorage.getItem('${STORAGE_KEYS.THEME}')`)
  })
})
