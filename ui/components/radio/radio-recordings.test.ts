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
    programmes: vi.fn().mockResolvedValue([]),
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
  await wrapper.find('button[aria-label="Kustuta: Keskpäevatund L 17.10"]').trigger('click')
  await flushPromises()
  return wrapper
}

describe('RadioRecordings', () => {
  beforeEach(() => {
    confirm.mockReset().mockResolvedValue(true)
    vi.mocked(radioService.deleteRecording).mockReset().mockResolvedValue()
    toasts.value = []
    vi.mocked(radioService.programmes).mockClear()
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
    expect((await mount()).text()).toContain('L 17.10 · 11:55–13:05')
  })

  it('should show a recording over the airing it was planned for', async () => {
    const noon = Date.UTC(2026, 9, 17, 9) / 1000
    vi.mocked(radioService.recordings).mockResolvedValue([
      recording({ span: { start: noon, end: noon + 3600 } }),
    ])
    expect((await mount()).text()).toContain('L 17.10 · 12:00–13:00')
  })

  it('should count the suggested cuts of a recording', async () => {
    expect((await mount()).text()).toContain('4 soovitust')
  })

  it('should say a transcribed recording has its transcript', async () => {
    expect((await mount()).text()).toContain('Olemas')
  })

  it('should not bold a transcript', async () => {
    expect((await mount()).find('[data-testid="radio-transcript"]').classes()).not.toContain(
      'font-semibold'
    )
  })

  it('should say a recording in progress is being recorded', async () => {
    expect((await mount()).text()).toContain('Salvestab')
  })

  it('should name each recording link after its programme, day and time', async () => {
    const link = (await mount()).find('a[href="/radio/2026-10-17"]')
    expect(link.attributes('aria-label')).toBe('Keskpäevatund L 17.10 · 11:55–13:05')
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

  it('should draw the row focus ring inside the row', async () => {
    const link = (await mount()).find('a[href="/radio/2026-10-17"]')
    expect(link.classes()).toContain('focus-visible:after:-outline-offset-2')
  })

  it('should stay on the list when the trash is clicked', async () => {
    expect((await remove()).vm.$router.currentRoute.value.path).toBe('/')
  })

  it('should open a recording that is still being recorded', async () => {
    expect((await mount()).find('a[href="/radio/2026-10-16"]').exists()).toBe(true)
  })

  it('should not offer a Kuula button', async () => {
    expect((await mount()).text()).not.toContain('Kuula')
  })

  it('should keep the trash above the row link', async () => {
    const trash = (await mount()).find('button[aria-label="Kustuta: Keskpäevatund L 17.10"]')
    expect(trash.classes()).toEqual(expect.arrayContaining(['z-10', 'max-md:min-h-11']))
  })

  it('should leave the actions empty for a recording still being recorded', async () => {
    const cells = (await mount()).findAll('tbody tr')[1]!.findAll('td')
    expect(cells[cells.length - 1]!.text()).toBe('')
  })

  it('should name the actions column for screen readers', async () => {
    expect((await mount()).find('thead th .sr-only').text()).toBe('Toimingud')
  })

  it('should ask before deleting a recording', async () => {
    await remove()
    expect(confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Kustuta salvestus?',
        message: expect.stringContaining('Keskpäevatund, L 17.10 · 11:55–13:05'),
        confirmText: 'Kustuta',
        cancelText: 'Loobu',
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

  it('should count the archive and the last recording again after a delete', async () => {
    await remove()
    const calls = [radioService.programmes, radioService.station].map(
      it => vi.mocked(it).mock.calls.length
    )
    expect(calls).toEqual([2, 2])
  })

  it('should say so when a recording cannot be deleted', async () => {
    vi.mocked(radioService.deleteRecording).mockRejectedValue(new Error('409'))
    await remove()
    expect(toasts.value.map(toast => [toast.message, toast.type])).toEqual([
      ['Salvestust ei saanud kustutada', 'error'],
    ])
  })

  it('should offer no delete for a recording still being recorded', async () => {
    const labels = (await mount())
      .findAll('button[aria-label^="Kustuta"]')
      .map(button => button.attributes('aria-label'))
    expect(new Set(labels)).toEqual(new Set(['Kustuta: Keskpäevatund L 17.10']))
  })
})
