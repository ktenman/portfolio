import { computed, ref, shallowRef, watch } from 'vue'
import type { RecordingSummary } from '../models/generated/domain-models'
import { radioService } from '../services/api'
import { recordingTime } from '../utils/radio'

export type Version = 'raw' | 'episode'

const LEAD = 5

const element = shallowRef<HTMLAudioElement | null>(null)
const recording = shallowRef<RecordingSummary | null>(null)
const live = ref(false)
const version = ref<Version>('raw')
const part = ref(0)
const current = ref(0)
const playing = ref(false)
const rate = ref(1)
const volume = ref(1)
const muted = ref(false)
const lost = ref(false)
let loaded = ''
let plan: { jumpAt: number; to: number; stopAt: number; jumped: boolean } | null = null

const parts = computed(() =>
  [...(recording.value?.status.parts ?? [])].sort((a, b) => a.offsetSeconds - b.offsetSeconds)
)
const offset = computed(() => parts.value[part.value]?.offsetSeconds ?? 0)
const seconds = computed(() =>
  version.value === 'raw' ? offset.value + current.value : current.value
)
const recordingSeconds = computed(() =>
  version.value === 'raw'
    ? seconds.value
    : recordingTime(recording.value?.audible ?? [], seconds.value)
)
const total = computed(() =>
  version.value === 'raw'
    ? (recording.value?.status.seconds ?? 0)
    : (recording.value?.editList?.durationSeconds ?? 0)
)

const url = (target: RecordingSummary, file: string) => radioService.fileUrl(target.name, file)

const source = (index: number) => {
  const audio = element.value
  const target = recording.value
  if (!audio || !target) return
  part.value = index
  audio.src = url(target, parts.value[index]?.file ?? 'raw.mp3')
}

const resume = () => {
  if (playing.value) element.value?.play()
}

const seek = (to: number) => {
  const audio = element.value
  if (!audio || !recording.value) return
  lost.value = false
  const target = Math.min(Math.max(0, to), total.value)
  if (version.value === 'episode') {
    audio.currentTime = target
    current.value = target
    return
  }
  const index = parts.value.reduce((found, p, i) => (p.offsetSeconds <= target ? i : found), 0)
  if (index !== part.value) {
    source(index)
    resume()
  }
  audio.currentTime = target - offset.value
  current.value = audio.currentTime
}

const open = () => {
  const audio = element.value
  const target = recording.value
  if (!audio || !target) return
  const key = `${target.name}|${version.value}|${version.value === 'episode' ? target.editList?.renderedAt : ''}`
  if (key === loaded) return
  loaded = key
  current.value = 0
  playing.value = false
  plan = null
  if (version.value === 'raw') return source(0)
  const edit = target.editList
  audio.src = `${url(target, 'episode.m4a')}?v=${encodeURIComponent(edit?.renderedAt ?? '')}`
}

const speed = (audio: HTMLAudioElement, value: number) => {
  audio.defaultPlaybackRate = value
  audio.playbackRate = value
}

const load = (target: RecordingSummary, wanted: Version = 'raw') => {
  recording.value = target
  version.value = wanted
  live.value = false
  if (element.value) speed(element.value, rate.value)
  open()
}

const play = () => element.value?.play()

const listen = () => {
  const audio = element.value
  if (!audio) return
  recording.value = null
  live.value = true
  loaded = 'live'
  plan = null
  current.value = 0
  audio.src = radioService.liveUrl
  speed(audio, 1)
  play()
}

const hangUp = () => {
  const audio = element.value
  if (!audio) return
  audio.pause()
  audio.removeAttribute('src')
  audio.load()
}

const toggle = () => {
  if (live.value) return playing.value ? hangUp() : listen()
  return playing.value ? element.value?.pause() : play()
}

const skip = (by: number) => seek(seconds.value + by)

const playFrom = (at: number, wanted: Version = 'raw') => {
  if (recording.value && version.value !== wanted) load(recording.value, wanted)
  plan = null
  seek(at)
  play()
}

const preview = (start: number, end: number) => {
  playFrom(start - LEAD)
  plan = { jumpAt: start, to: end, stopAt: end + LEAD, jumped: false }
}

const follow = () => {
  if (!plan) return
  if (!plan.jumped && seconds.value >= plan.jumpAt) {
    plan.jumped = true
    seek(plan.to)
    return
  }
  if (plan.jumped && seconds.value >= plan.stopAt) {
    plan = null
    element.value?.pause()
  }
}

const rejoin = () => (lost.value = false)

const listeners: Record<string, () => void> = {
  timeupdate: () => {
    current.value = element.value?.currentTime ?? 0
    follow()
  },
  play: () => (playing.value = true),
  pause: () => (playing.value = false),
  ended: () => {
    const next = parts.value[part.value + 1]
    if (version.value === 'raw' && next) {
      seek(Math.max(offset.value + (element.value?.duration || 0), next.offsetSeconds))
      return play()
    }
    playing.value = false
  },
}

const attach = (audio: HTMLAudioElement) => {
  element.value = audio
  Object.entries(listeners).forEach(([name, listener]) => audio.addEventListener(name, listener))
  speed(audio, rate.value)
  audio.volume = volume.value
  audio.muted = muted.value
  open()
}

const detach = () => {
  const audio = element.value
  if (audio) {
    audio.pause()
    Object.entries(listeners).forEach(([name, listener]) =>
      audio.removeEventListener(name, listener)
    )
  }
  element.value = null
  loaded = ''
  recording.value = null
  live.value = false
  version.value = 'raw'
  part.value = 0
  current.value = 0
  playing.value = false
  lost.value = false
  muted.value = false
  plan = null
}

watch(rate, value => {
  if (element.value && !live.value) speed(element.value, value)
})

watch(volume, value => {
  if (element.value) element.value.volume = value
})

watch(muted, value => {
  if (element.value) element.value.muted = value
})

export const useRadioPlayer = () => ({
  recording,
  live,
  version,
  seconds,
  recordingSeconds,
  total,
  playing,
  rate,
  volume,
  muted,
  lost,
  rejoin,
  attach,
  detach,
  load,
  listen,
  toggle,
  seek,
  skip,
  playFrom,
  preview,
})
