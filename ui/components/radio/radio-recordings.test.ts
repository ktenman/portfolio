import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioRecordings from './radio-recordings.vue'
import { recording } from './radio-fixture'
import { CutState, RecordingState, TranscriptState } from '../../models/generated/domain-models'
import { toasts } from '../../composables/use-toast'

const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }))

vi.mock('../../services/api', () => ({
  radioService: {
    recordings: vi.fn(),
    deleteRecording: vi.fn(),
    station: vi.fn().mockResolvedValue(null),
  },
}))
vi.mock('../../composables/use-confirm', () => ({ useConfirm: () => ({ confirm }) }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const mount = async () => {
  const wrapper = renderWithProviders(RadioRecordings)
  await flushPromises()
  return wrapper
}

const remove = async () => {
  const wrapper = await mount()
  await wrapper.find('button[aria-label="Delete: Keskpäevatund Sat 17.10"]').trigger('click')
  await flushPromises()
  return wrapper
}

describe('RadioRecordings', () => {
  beforeEach(() => {
    confirm.mockReset().mockResolvedValue(true)
    vi.mocked(radioService.deleteRecording).mockReset().mockResolvedValue()
    toasts.value = []
    vi.mocked(radioService.station).mockClear()
    vi.mocked(radioService.recordings).mockResolvedValue([
      recording(),
      recording({
        name: '2026-10-16',
        status: {
          state: RecordingState.RECORDING,
          programmes: ['Keskpäevatund'],
          startedAt: 1792170142,
          lostSeconds: 0,
          slot: null,
          seconds: 60,
          parts: [],
        },
        sizeBytes: 1_600_000,
        transcribed: false,
        editList: null,
        episodeBytes: null,
        cuts: [],
        timeline: [],
        transcriptState: TranscriptState.RECORDING,
        cutState: CutState.NONE,
      }),
    ])
  })

  it('should show when and how long the show was recorded', async () => {
    expect((await mount()).text()).toContain('Sat 17.10 · 11:55–13:05')
  })

  it('should show a recording over the airing it was planned for', async () => {
    const noon = Date.UTC(2026, 9, 17, 9) / 1000
    vi.mocked(radioService.recordings).mockResolvedValue([
      recording({ span: { start: noon, end: noon + 3600 } }),
    ])
    expect((await mount()).text()).toContain('Sat 17.10 · 12:00–13:00')
  })

  it('should leave the cuts out of the table', async () => {
    expect((await mount()).text()).not.toContain('Cuts')
  })

  it('should say a transcribed recording has its transcript', async () => {
    expect((await mount()).text()).toContain('Ready')
  })

  it('should not bold a transcript', async () => {
    expect((await mount()).find('[data-testid="radio-transcript"]').classes()).not.toContain(
      'font-semibold'
    )
  })

  it('should say a recording in progress is being recorded', async () => {
    expect((await mount()).findAll('tbody tr')[1]!.text()).toContain('Recording')
  })

  it('should name each recording link after its programme, day and time', async () => {
    const link = (await mount()).find('a[href="/radio/2026-10-17"]')
    expect(link.attributes('aria-label')).toBe('Keskpäevatund Sat 17.10 · 11:55–13:05')
  })

  it('should stretch the recording link over its row', async () => {
    const wrapper = await mount()
    const stretched = [
      ...wrapper.find('a[href="/radio/2026-10-17"]').classes(),
      ...wrapper.find('tbody tr').classes(),
    ]
    expect(stretched).toEqual(
      expect.arrayContaining(['after:absolute', 'after:inset-0', 'relative'])
    )
  })

  it('should keep the brass focus outline every link takes on the recording link', async () => {
    const link = (await mount()).find('a[href="/radio/2026-10-17"]')
    expect(link.classes()).not.toContain('focus-visible:outline-none')
  })

  it('should stay on the list when the trash is clicked', async () => {
    expect((await remove()).vm.$router.currentRoute.value.path).toBe('/')
  })

  it('should open a recording that is still being recorded', async () => {
    expect((await mount()).find('a[href="/radio/2026-10-16"]').exists()).toBe(true)
  })

  it('should not offer a Listen button', async () => {
    expect((await mount()).text()).not.toContain('Listen')
  })

  it('should keep the trash above the row link', async () => {
    const trash = (await mount()).find('button[aria-label="Delete: Keskpäevatund Sat 17.10"]')
    expect(trash.classes()).toContain('z-10')
  })

  it('should give a phone row the cleaned episode length', async () => {
    expect((await mount()).get('[data-testid="radio-recording-row"]').text()).toContain('42 min')
  })

  it('should give a phone row the time the recording started', async () => {
    expect((await mount()).get('[data-testid="radio-recording-row"]').text()).toMatch(
      /11:55 ·\s+42 min/
    )
  })

  it('should call a phone row without an edit list not cleaned', async () => {
    vi.mocked(radioService.recordings).mockResolvedValue([recording({ editList: null })])
    expect((await mount()).get('[data-testid="radio-recording-row"]').text()).toContain(
      'not cleaned'
    )
  })

  it('should mark a phone row that is still being recorded', async () => {
    const rows = (await mount()).findAll('[data-testid="radio-recording-row"]')
    expect(rows[1]!.text()).toContain('recording')
  })

  it('should leave the actions empty for a recording still being recorded', async () => {
    const cells = (await mount()).findAll('tbody tr')[1]!.findAll('td')
    expect(cells[cells.length - 1]!.text()).toBe('')
  })

  it('should name the actions column for screen readers', async () => {
    expect((await mount()).find('thead th .sr-only').text()).toBe('Actions')
  })

  it('should ask before deleting a recording', async () => {
    await remove()
    expect(confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Delete recording?',
        message: expect.stringContaining('Keskpäevatund, Sat 17.10 · 11:55–13:05'),
        confirmText: 'Delete',
        cancelText: 'Cancel',
      })
    )
  })

  it('should keep the recording when the user cancels the delete', async () => {
    confirm.mockResolvedValue(false)
    await remove()
    expect(radioService.deleteRecording).not.toHaveBeenCalled()
  })

  it('should delete the recording once the user confirms', async () => {
    await remove()
    expect(radioService.deleteRecording).toHaveBeenCalledWith('2026-10-17')
  })

  it('should list the recordings again after a delete', async () => {
    await remove()
    expect(radioService.recordings).toHaveBeenCalledTimes(2)
  })

  it('should count the last recording again after a delete', async () => {
    await remove()
    expect(radioService.station).toHaveBeenCalledTimes(2)
  })

  it('should mark the recordings of each show stale after a delete', async () => {
    const wrapper = await mount()
    wrapper.queryClient.setQueryDefaults(['radio-programmes'], { gcTime: Infinity })
    wrapper.queryClient.setQueryData(['radio-programmes'], [])
    await wrapper.find('button[aria-label="Delete: Keskpäevatund Sat 17.10"]').trigger('click')
    await flushPromises()
    expect(wrapper.queryClient.getQueryState(['radio-programmes'])?.isInvalidated).toBe(true)
  })

  it('should mark the search results stale after a delete', async () => {
    const wrapper = await mount()
    wrapper.queryClient.setQueryDefaults(['radio', 'search'], { gcTime: Infinity })
    wrapper.queryClient.setQueryData(['radio', 'search', 'valimis'], {})
    await wrapper.find('button[aria-label="Delete: Keskpäevatund Sat 17.10"]').trigger('click')
    await flushPromises()
    expect(wrapper.queryClient.getQueryState(['radio', 'search', 'valimis'])?.isInvalidated).toBe(
      true
    )
  })

  it('should say so when a recording cannot be deleted', async () => {
    vi.mocked(radioService.deleteRecording).mockRejectedValue(new Error('409'))
    await remove()
    expect(toasts.value.map(toast => [toast.message, toast.type])).toEqual([
      ['Could not delete the recording', 'error'],
    ])
  })

  it('should offer no delete for a recording still being recorded', async () => {
    const labels = (await mount())
      .findAll('button[aria-label^="Delete"]')
      .map(button => button.attributes('aria-label'))
    expect(new Set(labels)).toEqual(new Set(['Delete: Keskpäevatund Sat 17.10']))
  })
})
