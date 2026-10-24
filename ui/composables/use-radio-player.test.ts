import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { recording } from '../components/radio/radio-fixture'
import { RecordingState } from '../models/generated/domain-models'

const positions = vi.hoisted(() => ({ find: vi.fn(), save: vi.fn() }))

vi.mock('../services/api', () => ({
  radioService: {
    fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}`,
    liveUrl: '/api/radio/live',
    position: positions.find,
    savePosition: positions.save,
  },
}))

const split = () =>
  recording({
    status: {
      state: RecordingState.PARTIAL,
      programmes: ['Keskpäevatund'],
      startedAt: 1792227300,
      lostSeconds: 0,
      slot: null,
      seconds: 4200,
      parts: [
        { file: 'raw.mp3', offsetSeconds: 0, sha256: null, durationSeconds: null },
        { file: 'raw-part-002.mp3', offsetSeconds: 2000, sha256: null, durationSeconds: null },
      ],
    },
  })

describe('useRadioPlayer', () => {
  let audio: HTMLAudioElement
  let player: ReturnType<typeof import('./use-radio-player').useRadioPlayer>

  const reload = async () => {
    vi.resetModules()
    const { useRadioPlayer } = await import('./use-radio-player')
    player = useRadioPlayer()
    audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    vi.spyOn(audio, 'pause').mockImplementation(() => {})
    vi.spyOn(audio, 'load').mockImplementation(() => {})
    player.attach(audio)
  }

  beforeEach(async () => {
    positions.find.mockReset().mockResolvedValue({ seconds: 0 })
    positions.save.mockReset().mockResolvedValue(undefined)
    await reload()
  })

  afterEach(() => player.detach())

  const tick = (seconds: number) => {
    audio.currentTime = seconds
    audio.dispatchEvent(new Event('timeupdate'))
  }

  it('should play the station live through the server', () => {
    player.listen()
    expect([audio.src.endsWith('/api/radio/live'), player.live.value]).toEqual([true, true])
  })

  it('should drop the live stream when it is stopped', () => {
    player.listen()
    audio.dispatchEvent(new Event('play'))
    player.toggle()
    expect(audio.getAttribute('src')).toBeNull()
  })

  it('should stop playing when the live stream is stopped', () => {
    player.listen()
    audio.dispatchEvent(new Event('play'))
    player.toggle()
    expect(player.playing.value).toBe(false)
  })

  it('should leave the live stream when a recording is loaded', () => {
    player.listen()
    player.load(split())
    expect(player.live.value).toBe(false)
  })

  it('should play at the volume chosen on the slider', async () => {
    player.listen()
    player.volume.value = 0.35
    await nextTick()
    expect(audio.volume).toBe(0.35)
  })

  it('should play the station at normal speed', () => {
    player.rate.value = 1.5
    player.listen()
    expect(audio.playbackRate).toBe(1)
  })

  it('should load the first part of the raw recording', () => {
    player.load(split())
    expect(audio.src).toContain('/api/radio/2026-10-17/raw.mp3')
  })

  it('should load the part that holds the moment it seeks to', () => {
    player.load(split())
    player.seek(2500)
    expect([audio.src.split('/').pop(), audio.currentTime]).toEqual(['raw-part-002.mp3', 500])
  })

  it('should give the position on the recording clock inside a later part', () => {
    player.load(split())
    player.seek(2500)
    tick(510)
    expect(player.seconds.value).toBe(2510)
  })

  it('should move on to the next part when a part ends', () => {
    player.load(split())
    audio.dispatchEvent(new Event('ended'))
    expect(audio.src.split('/').pop()).toBe('raw-part-002.mp3')
  })

  it('should load the episode with its render time so a new render is not read from the cache', () => {
    player.load(recording(), 'episode')
    expect(audio.src).toContain('/api/radio/2026-10-17/episode.m4a?v=2026-10-17T10%3A20%3A00Z')
  })

  it('should load a new render of the episode it already plays', () => {
    player.load(recording(), 'episode')
    const edit = recording().editList!
    player.load(recording({ editList: { ...edit, renderedAt: '2026-10-18T09:00:00Z' } }), 'episode')
    expect(audio.src).toContain('v=2026-10-18T09%3A00%3A00Z')
  })

  it('should map the episode position onto the recording', () => {
    player.load(recording(), 'episode')
    tick(1400)
    expect(player.recordingSeconds.value).toBe(2296.5)
  })

  it('should start a transition preview five seconds before the cut', () => {
    player.load(recording())
    player.preview(2120, 2255)
    expect(audio.currentTime).toBe(2115)
  })

  it('should jump over the cut during a transition preview', () => {
    player.load(recording())
    player.preview(2120, 2255)
    tick(2120.1)
    expect(audio.currentTime).toBe(2255)
  })

  it('should stop a transition preview five seconds after the cut', () => {
    player.load(recording())
    player.preview(2120, 2255)
    tick(2120.1)
    tick(2260.2)
    expect(audio.pause).toHaveBeenCalled()
  })

  it('should continue in the next part where the finished part ended', () => {
    player.load(split())
    Object.defineProperty(audio, 'duration', { value: 2010, configurable: true })
    audio.dispatchEvent(new Event('ended'))
    expect([audio.src.split('/').pop(), audio.currentTime]).toEqual(['raw-part-002.mp3', 10])
  })

  it('should show the player as stopped after switching version while playing', () => {
    player.load(recording())
    audio.dispatchEvent(new Event('play'))
    player.load(recording(), 'episode')
    expect(player.playing.value).toBe(false)
  })

  it('should keep the chosen speed when the source changes', async () => {
    player.rate.value = 1.5
    await nextTick()
    player.load(recording())
    expect(audio.defaultPlaybackRate).toBe(1.5)
  })

  it('should load a recording asked for before the audio element was attached', () => {
    player.detach()
    player.load(recording(), 'episode')
    player.attach(audio)
    expect(audio.src).toContain('episode.m4a')
  })

  it('should map the episode position over a gap between parts onto the recording', () => {
    player.load(
      recording({
        audible: [
          { start: 762, end: 1500 },
          { start: 1530, end: 2120 },
        ],
      }),
      'episode'
    )
    tick(800)
    expect(player.recordingSeconds.value).toBe(1592)
  })

  it('should mute the audio without losing its level', async () => {
    player.volume.value = 0.6
    player.muted.value = true
    await nextTick()
    expect([audio.muted, audio.volume]).toEqual([true, 0.6])
  })

  it('should keep the moment on air when switching to the episode', () => {
    player.load(recording())
    tick(2296.5)
    player.swap('episode')
    expect([player.version.value, audio.currentTime]).toEqual(['episode', 1400])
  })

  it('should keep the moment on air when switching to the recording', () => {
    player.load(recording(), 'episode')
    tick(1400)
    player.swap('raw')
    expect([player.version.value, player.recordingSeconds.value]).toEqual(['raw', 2296.5])
  })

  it('should keep playing after switching version while playing', () => {
    player.load(recording())
    audio.dispatchEvent(new Event('play'))
    vi.mocked(audio.play).mockClear()
    player.swap('episode')
    expect(audio.play).toHaveBeenCalledOnce()
  })

  it('should stay paused after switching version while paused', () => {
    player.load(recording())
    player.swap('episode')
    expect(audio.play).not.toHaveBeenCalled()
  })

  it('should play the cleaned episode from a chapter start', () => {
    player.load(recording(), 'raw')
    player.playFrom(120, 'episode')
    expect([player.version.value, audio.currentTime]).toEqual(['episode', 120])
  })

  it('should play the raw recording from a moment chosen in the episode version', () => {
    player.load(recording(), 'episode')
    player.playFrom(762)
    expect([player.version.value, audio.currentTime]).toEqual(['raw', 762])
  })

  it('should stop being lost after a seek', () => {
    player.load(recording())
    player.lost.value = true
    player.seek(120)
    expect(player.lost.value).toBe(false)
  })

  it('should stop being lost after playing from a line', () => {
    player.load(recording())
    player.lost.value = true
    player.playFrom(120)
    expect(player.lost.value).toBe(false)
  })

  it('should forget being lost when the player is detached', () => {
    player.lost.value = true
    player.detach()
    expect(player.lost.value).toBe(false)
  })

  it('should start a recording where the listener left it', async () => {
    positions.find.mockResolvedValue({ seconds: 2296.5 })
    player.load(recording(), 'episode')
    await flushPromises()
    expect(audio.currentTime).toBe(1400)
  })

  it('should start a raw recording in the part where the listener left it', async () => {
    positions.find.mockResolvedValue({ seconds: 2510 })
    player.load(split())
    await flushPromises()
    expect([audio.src.split('/').pop(), audio.currentTime]).toEqual(['raw-part-002.mp3', 510])
  })

  it('should start another recording from its own beginning', async () => {
    positions.find.mockImplementation(async (name: string) => ({
      seconds: name === '2026-10-17' ? 2296.5 : 0,
    }))
    player.load(recording(), 'episode')
    await flushPromises()
    player.load(recording({ name: '2026-10-18' }), 'episode')
    await flushPromises()
    expect(player.seconds.value).toBe(0)
  })

  it('should not move a recording opened before the place of the previous one arrived', async () => {
    let answer: (position: { seconds: number }) => void = () => {}
    positions.find.mockReturnValueOnce(new Promise(resolve => (answer = resolve)))
    player.load(recording(), 'episode')
    player.load(recording({ name: '2026-10-18' }), 'episode')
    answer({ seconds: 2296.5 })
    await flushPromises()
    expect(player.seconds.value).toBe(0)
  })

  it('should keep playing from the time the listener picked before the place of the recording arrived', async () => {
    let answer: (position: { seconds: number }) => void = () => {}
    positions.find.mockReturnValueOnce(new Promise(resolve => (answer = resolve)))
    player.load(recording())
    player.playFrom(2510)
    answer({ seconds: 1400 })
    await flushPromises()
    expect(player.seconds.value).toBe(2510)
  })

  it('should start the next recording where the listener left it after a time picked in another one', async () => {
    positions.find.mockImplementation((name: string) =>
      Promise.resolve({ seconds: name === '2026-10-18' ? 1400 : 0 })
    )
    player.load(recording())
    await flushPromises()
    player.playFrom(600)
    player.load(recording({ name: '2026-10-18' }))
    await flushPromises()
    expect(player.seconds.value).toBe(1400)
  })

  it('should not ask again where the recording was left when its version changes', async () => {
    player.load(recording(), 'episode')
    await flushPromises()
    player.swap('raw')
    expect(positions.find).toHaveBeenCalledTimes(1)
  })

  it('should keep the place in the recording when a new render of the episode arrives', async () => {
    player.load(recording(), 'episode')
    await flushPromises()
    tick(1400)
    const edit = recording().editList!
    player.load(recording({ editList: { ...edit, renderedAt: '2026-10-18T09:00:00Z' } }), 'episode')
    expect(player.seconds.value).toBe(1400)
  })

  it('should save where the listener is after every ten seconds of listening', async () => {
    player.load(recording())
    await flushPromises()
    tick(5)
    tick(10)
    tick(15)
    expect(positions.save.mock.calls).toEqual([['2026-10-17', { seconds: 10 }]])
  })

  it('should not save where the listener paused before the place of the recording arrived', () => {
    positions.find.mockReturnValue(new Promise(() => {}))
    player.load(recording())
    tick(600)
    audio.dispatchEvent(new Event('pause'))
    expect(positions.save).not.toHaveBeenCalled()
  })

  it('should save where the listener paused', async () => {
    positions.find.mockResolvedValue({ seconds: 600 })
    player.load(recording())
    await flushPromises()
    tick(604)
    audio.dispatchEvent(new Event('pause'))
    expect(positions.save.mock.calls).toEqual([['2026-10-17', { seconds: 604 }]])
  })

  it('should start a recording heard to the end from the beginning next time', async () => {
    positions.find.mockResolvedValue({ seconds: 4195 })
    player.load(recording())
    await flushPromises()
    tick(4198)
    Object.defineProperty(audio, 'ended', { value: true })
    audio.dispatchEvent(new Event('pause'))
    audio.dispatchEvent(new Event('ended'))
    expect(positions.save.mock.calls).toEqual([['2026-10-17', { seconds: 0 }]])
  })

  it('should tell the listener when the place of the recording cannot be found', async () => {
    positions.find.mockRejectedValue(new Error('Bad Gateway'))
    const { toasts } = await import('./use-toast')
    player.load(recording())
    await flushPromises()
    expect(toasts.value.map(toast => toast.message)).toEqual([
      'Could not find where you left the recording',
    ])
  })
})
