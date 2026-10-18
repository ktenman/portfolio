import { API_ENDPOINTS } from '../../constants/api'
import { contexts, recording, transcript } from '../../components/radio/radio-fixture'
import { CutState, RecordingState, TranscriptState } from '../../models/generated/domain-models'
import { apiRoute, type RouteStub } from './stub'

const NAME = '2026-10-17'

const RECORDINGS = [
  recording({
    name: '2026-10-24',
    status: {
      state: RecordingState.RECORDING,
      startedAt: Date.UTC(2026, 9, 24, 8, 55, 0) / 1000,
      seconds: 1260,
      parts: [{ file: 'raw.mp3', offsetSeconds: 0, sha256: null }],
    },
    sizeBytes: 40_300_000,
    transcribed: false,
    editList: null,
    episodeBytes: null,
    cuts: [],
    timeline: [],
    transcriptState: TranscriptState.RECORDING,
    cutState: CutState.NONE,
  }),
  recording(),
]

export const stubRadio: RouteStub = async page => {
  await page.route(/\/api\/radio\/[^/]+\/[^/]+\.(mp3|m4a)/, route => route.abort())
  await page.route(apiRoute(API_ENDPOINTS.RADIO), route => route.fulfill({ json: RECORDINGS }))
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/${NAME}/cuts`), route =>
    route.fulfill({ json: contexts() })
  )
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/${NAME}/transcript`), route =>
    route.fulfill({ json: transcript() })
  )
}
