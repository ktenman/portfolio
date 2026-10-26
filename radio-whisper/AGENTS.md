# Whisper Worker

Python worker that records Kuku's live HLS stream and transcribes it while it records, with the TalTech Whisper model. The Kotlin worker in `radio-worker/` replaced it (#2283), and it is kept as a backup: TalTech's Whisper reads better than the Zipformer (#2284) but takes 13 min per 10 min of audio on fov.ee. A replay station serves a saved recording as a live stream, so the whole run can be tested offline (#2064).

The worker stops at `status.json`, `sounds.json` and `transcript.json`. The backend's `RadioEpisodeJob` reads them and makes the ad-free `episode.m4a` and its `edit-list.json` in the same directory (#2065).

## Tech Stack

Python 3.12, uv, faster-whisper 1.2.1 (CTranslate2 int8), ai-edge-litert 2.3.0 (YAMNet), numpy, FFmpeg and ffprobe, jemalloc 5.3, pytest

## Commands

```bash
uv run pytest                                      # Tests: no network, no model
uv run radio-worker station <recording.mp3>        # Replay at http://127.0.0.1:8770/stream.m3u8
uv run radio-worker model <directory>              # The pinned TalTech model into <directory>/ct2, checked by SHA-256
uv run radio-worker run --duration <seconds> --out <directory> --model <ct2 directory>   # Kuku live
uv run radio-worker run --source <recording.mp3> --start <ISO time> --out <directory> --model <ct2 directory>   # A file, to its end
uv run radio-worker planned --recordings <directory> --model <directory>     # Every capture the backend lists in <directory>/plan.json
uv run radio-worker sounds <directory>             # Speech and music scores of a finished recording into <directory>/sounds.json
docker build -t radio-whisper .                    # Image without the model, which is mounted at /model
docker build --target test -t radio-whisper-test . && docker run --rm radio-whisper-test   # Tests inside the image, as CI runs them
docker network create radio
docker run -d --rm --network radio --name station -p 127.0.0.1:8770:8770 -v <absolute recording.mp3 path>:/replay.mp3:ro radio-whisper station /replay.mp3 --host 0.0.0.0
docker run --rm --network radio -v <absolute ct2 directory>:/model:ro -v <absolute directory owned by uid 1000>:/recordings radio-whisper run --source http://station:8770/stream.m3u8 --duration <seconds> --out /recordings/<name> --model /model
```

## Structure

- `src/radio_worker/station.py` - Replay station: FFmpeg's HLS muxer behind `http.server`
- `src/radio_worker/recorder.py` - Records the stream into parts and owns `status.json`
- `src/radio_worker/transcriber.py` - Live transcript in chunks cut at pauses, from each part's 16 kHz PCM; it writes `sounds.json` before `transcript.json`
- `src/radio_worker/sounds.py` - Speech and music scores every 0.5 s from the bundled YAMNet model (`yamnet.tflite`, Apache 2.0); the backend cuts the songs inside a show from them
- `src/radio_worker/schedule.py` - Follows the backend's `plan.json`, re-read every minute, and downloads the pinned model's `ct2/` files, checked by SHA-256, between captures; it leaves `heartbeat.json` with the time it will next be seen, so the backend can tell a stopped recorder; it re-executes the worker after each capture and once the model is in place, so every capture starts in a fresh process (#2219)
- `src/radio_worker/cli.py` - The `station`, `run`, `planned` and `sounds` commands
- `tests/support.py` - Generated tone, synthetic PCM recordings, a stub model and station helpers shared by the tests
- `Dockerfile` - Python 3.12 slim with FFmpeg and the `uv.lock` dependencies, run as the non-root `worker`; its `test` stage adds the dev dependencies and `tests/`. It preloads jemalloc with `oversize_threshold:0`, which keeps the encoder's 180 MB attention buffers mapped from layer to layer, where glibc unmaps them and faults them in again (#2243)

## From a fresh clone

`npm run dev` points the backend at the gitignored `radio-data/recordings`. From `radio-whisper/`:

```bash
uv run radio-worker model ../radio-data/model
uv run radio-worker run --duration 600 --programme Kuku --out ../radio-data/recordings/2026-10-18-1200-kuku --model ../radio-data/model/ct2
uv run radio-worker run --source ~/keskpaevatund.mp3 --start 2026-10-17T11:55 --programme Keskpäevatund --out ../radio-data/recordings/2026-10-17-1155-keskpaevatund --model ../radio-data/model/ct2
```

The directory name is the recording's name in the Raadio tab: lowercase letters, digits and hyphens. `--start` is the time the recording went on air, in Tallinn time; without it, the start is now. A file is read to its end as fast as FFmpeg can; Kuku live takes `--duration`. Cuts and topics also need `OPENROUTER_API_KEY` in the backend; the transcript shows without it.

## Production

The `radio-whisper` service in `docker-compose.yml` runs `planned` with the Kotlin worker's caps, in the `whisper` Compose profile, which the deploy never enables. It mounts `radio_model`, which still holds the model, and `radio_recordings`. Never run it beside the Kotlin worker: both would record the same captures into the same directories. To switch production to Whisper, set the repository variable `RADIO_ENABLED` to `false`, so deploys leave the Kotlin worker stopped, then run `docker compose --profile whisper pull radio-whisper && docker compose --profile radio stop radio-worker && docker compose --profile whisper up -d radio-whisper` in githubuser's home on fov.ee; deploys never pull the backup, so the pull fetches its latest image. To switch back, set `RADIO_ENABLED` to `true` and rerun the deploy, which stops the backup before it starts the Kotlin worker. A switch during a capture hands it over: the other worker resumes it, and its transcript mixes both models.

## Rules

- Never write over a recording: FFmpeg runs with `-n`, and an existing directory is reused only for this worker's own unfinished recording
- Place each part by the stream timestamp FFmpeg reports for its first packet, never by the clock
- Tests use generated MP3, synthetic audio, a stub Whisper model and the bundled YAMNet; they never reach Kuku or Hugging Face
- Test names are full sentences: `test_station_goes_off_air_after_the_replay_ends`
- Run from project root: `npm run test:whisper`
- CI runs the tests in the `Dockerfile`'s `test` stage, so a command only a test calls (such as `pkill`) is installed there, not in the runtime image
