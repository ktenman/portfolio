# Radio Worker

Python worker that records Kuku's live HLS stream and transcribes it while it records, with the TalTech Whisper model. A replay station serves a saved recording as a live stream, so the whole run can be tested offline (#2064).

The worker stops at `status.json` and `transcript.json`. The backend's `RadioEpisodeJob` reads them and makes the ad-free `episode.m4a` and its `edit-list.json` in the same directory (#2065).

## Tech Stack

Python 3.12, uv, faster-whisper 1.2.1 (CTranslate2 int8), numpy, FFmpeg and ffprobe, pytest

## Commands

```bash
uv run pytest                                      # Tests: no network, no model
uv run radio-worker station <recording.mp3>        # Replay at http://127.0.0.1:8770/stream.m3u8
uv run radio-worker run --duration <seconds> --out <directory> --model <ct2 directory>
uv run radio-worker saturdays --recordings <directory> --model <directory>   # Every Saturday 11:55-13:05 Europe/Tallinn into <directory>/<date>
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
- `src/radio_worker/schedule.py` - The Saturday window, and the one-time download of the pinned model's `ct2/` files, checked by SHA-256, outside it
- `src/radio_worker/cli.py` - The `station`, `run` and `saturdays` commands
- `tests/support.py` - Generated tone, synthetic PCM recordings, a stub model and station helpers shared by the tests
- `Dockerfile` - Python 3.12 slim with FFmpeg and the `uv.lock` dependencies, run as the non-root `worker`; its `test` stage adds the dev dependencies and `tests/`

## Production

The `radio-worker` service in `docker-compose.yml` runs `saturdays` with the capped CPU, memory and swap measured in #2066, on its own network with no published ports. It sits in the `radio` Compose profile, which the deploy enables only when the repository variable `RADIO_ENABLED` is `true`. The model lives in the `radio_model` volume; recordings go to `radio_recordings`, which the backend mounts at `/recordings`. Both images own their mount points as uid 1000, so whichever container creates a volume, both can write to it.

## Rules

- Never write over a recording: FFmpeg runs with `-n`, and an existing directory is reused only for this worker's own unfinished recording
- Place each part by the stream timestamp FFmpeg reports for its first packet, never by the clock
- Tests use generated MP3, synthetic audio and a stub model; they never reach Kuku or Hugging Face
- Test names are full sentences: `test_station_goes_off_air_after_the_replay_ends`
- Run from project root: `npm run test:radio`
- CI runs the tests in the `Dockerfile`'s `test` stage, so a command only a test calls (such as `pkill`) is installed there, not in the runtime image
