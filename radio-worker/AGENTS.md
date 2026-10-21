# Radio Worker

Python worker that records Kuku's live HLS stream and transcribes it while it records, with the TalTech Whisper model. A replay station serves a saved recording as a live stream, so the whole run can be tested offline (#2064).

The worker stops at `status.json` and `transcript.json`. The backend's `RadioEpisodeJob` reads them and makes the ad-free `episode.m4a` and its `edit-list.json` in the same directory (#2065).

## Tech Stack

Python 3.12, uv, faster-whisper 1.2.1 (CTranslate2 int8), numpy, FFmpeg and ffprobe, pytest

## Commands

```bash
uv run pytest                                      # Tests: no network, no model
uv run radio-worker station <recording.mp3>        # Replay at http://127.0.0.1:8770/stream.m3u8
uv run radio-worker model <directory>              # The pinned TalTech model into <directory>/ct2, checked by SHA-256
uv run radio-worker run --duration <seconds> --out <directory> --model <ct2 directory>   # Kuku live
uv run radio-worker run --source <recording.mp3> --start <ISO time> --out <directory> --model <ct2 directory>   # A file, to its end
uv run radio-worker planned --recordings <directory> --model <directory>     # Every capture the backend lists in <directory>/plan.json
docker build -t radio-worker .                     # Image without the model, which is mounted at /model
docker build --target test -t radio-worker-test . && docker run --rm radio-worker-test   # Tests inside the image, as CI runs them
docker network create radio
docker run -d --rm --network radio --name station -p 127.0.0.1:8770:8770 -v <absolute recording.mp3 path>:/replay.mp3:ro radio-worker station /replay.mp3 --host 0.0.0.0
docker run --rm --network radio -v <absolute ct2 directory>:/model:ro -v <absolute directory owned by uid 1000>:/recordings radio-worker run --source http://station:8770/stream.m3u8 --duration <seconds> --out /recordings/<name> --model /model
```

## Structure

- `src/radio_worker/station.py` - Replay station: FFmpeg's HLS muxer behind `http.server`
- `src/radio_worker/recorder.py` - Records the stream into parts and owns `status.json`
- `src/radio_worker/transcriber.py` - Live transcript in chunks cut at pauses, from each part's 16 kHz PCM
- `src/radio_worker/schedule.py` - Follows the backend's `plan.json`, re-read every minute, and downloads the pinned model's `ct2/` files, checked by SHA-256, between captures; it leaves `heartbeat.json` with the time it will next be seen, so the backend can tell a stopped recorder
- `src/radio_worker/cli.py` - The `station`, `run` and `planned` commands
- `tests/support.py` - Generated tone, synthetic PCM recordings, a stub model and station helpers shared by the tests
- `Dockerfile` - Python 3.12 slim with FFmpeg and the `uv.lock` dependencies, run as the non-root `worker`; its `test` stage adds the dev dependencies and `tests/`

## From a fresh clone

`npm run dev` points the backend at the gitignored `radio-data/recordings`. From `radio-worker/`:

```bash
uv run radio-worker model ../radio-data/model
uv run radio-worker run --duration 600 --programme Kuku --out ../radio-data/recordings/2026-10-18-1200-kuku --model ../radio-data/model/ct2
uv run radio-worker run --source ~/keskpaevatund.mp3 --start 2026-10-17T11:55 --programme Keskpäevatund --out ../radio-data/recordings/2026-10-17-1155-keskpaevatund --model ../radio-data/model/ct2
```

The directory name is the recording's name in the Raadio tab: lowercase letters, digits and hyphens. `--start` is the time the recording went on air, in Tallinn time; without it, the start is now. A file is read to its end as fast as FFmpeg can; Kuku live takes `--duration`. Cuts and topics also need `OPENROUTER_API_KEY` in the backend; the transcript shows without it.

The full Docker stack runs the worker as production does, recording every programme ticked „Iga kord“ into the `radio_recordings` volume:

```bash
SCHEDULING_ENABLED=true docker compose -f docker-compose.local.yml --profile radio up -d --build
```

## Production

The `radio-worker` service in `docker-compose.yml` runs `planned` with the capped CPU, memory and swap measured in #2066, on its own network with no published ports. It sits in the `radio` Compose profile, which the deploy enables only when the repository variable `RADIO_ENABLED` is `true`. The backend's `RadioScheduleJob` imports Kuku's schedule every night and writes `plan.json` into `radio_recordings`: each original airing of a programme ticked „Iga kord“, from 30 s before to 30 s after, with airings up to 10 min apart joined into one recording named `<date>-<HHMM>-<slug>[-<slug>…]` after the first airing's start. The worker writes the plan's programme names and air slot into `status.json`. The model lives in the `radio_model` volume; recordings go to `radio_recordings`, which the backend mounts at `/recordings`. Both images own their mount points as uid 1000, so whichever container creates a volume, both can write to it.

## Rules

- Never write over a recording: FFmpeg runs with `-n`, and an existing directory is reused only for this worker's own unfinished recording
- Place each part by the stream timestamp FFmpeg reports for its first packet, never by the clock
- Tests use generated MP3, synthetic audio and a stub model; they never reach Kuku or Hugging Face
- Test names are full sentences: `test_station_goes_off_air_after_the_replay_ends`
- Run from project root: `npm run test:radio`
- CI runs the tests in the `Dockerfile`'s `test` stage, so a command only a test calls (such as `pkill`) is installed there, not in the runtime image
