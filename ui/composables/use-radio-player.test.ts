import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useRadioPlayer } from './use-radio-player'
import { recording } from '../components/radio/radio-fixture'
import { RecordingState } from '../models/generated/domain-models'

vi.mock('../services/api', () => ({
  radioService: {
    fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}`,
    liveUrl: '/api/radio/live',
  },
}))

const split = () =>
  recording({
    status: {
      state: RecordingState.PARTIAL,
      programmes: ['Keskpäevatund'],
      startedAt: 1792227300,
      lostSeconds: 0,
      continuous: false,
      seconds: 4200,
      parts: [
        { file: 'raw.mp3', offsetSeconds: 0, sha256: null, durationSeconds: null },
        { file: 'raw-part-002.mp3', offsetSeconds: 2000, sha256: null, durationSeconds: null },
      ],
    },
  })

describe('useRadioPlayer', () => {
  let audio: HTMLAudioElement
  const player = useRadioPlayer()

  beforeEach(() => {
    audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    vi.spyOn(audio, 'pause').mockImplementation(() => {})
    vi.spyOn(audio, 'load').mockImplementation(() => {})
    player.rate.value = 1
    player.volume.value = 1
    player.attach(audio)
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

  it('should play the raw recording from a moment chosen in the episode version', () => {
    player.load(recording(), 'episode')
    player.playFrom(762)
    expect([player.version.value, audio.currentTime]).toEqual(['raw', 762])
  })
})
