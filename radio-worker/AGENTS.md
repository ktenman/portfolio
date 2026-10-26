# Radio Worker

Kotlin worker that records Kuku's live HLS stream and transcribes it while it records, with TalTech's streaming Zipformer. A replay station serves a saved recording as a live stream, so the whole run can be tested offline (#2064).

The worker stops at `status.json`, `sounds.json` and `transcript.json`. The backend's `RadioEpisodeJob` reads them and makes the ad-free `episode.m4a` and its `edit-list.json` in the same directory (#2065).

## Tech Stack

Kotlin 2.4, Java 25, sherpa-onnx 1.13.8 (TalTech's streaming Zipformer and CED-tiny, both int8), Jackson 3, FFmpeg and ffprobe, Gradle, JUnit 5 and Atrium

## Commands

```bash
../gradlew :radio-worker:test                    # Tests: no network, a stub recognizer, the bundled CED-tiny
../gradlew :radio-worker:installDist             # The worker into build/install/radio-worker
build/install/radio-worker/bin/radio-worker station <recording.mp3>      # Replay at http://127.0.0.1:8770/stream.m3u8
build/install/radio-worker/bin/radio-worker run --duration <seconds> --out <directory> --model <zipformer directory> --tagger ced-tiny   # Kuku live
build/install/radio-worker/bin/radio-worker run --source <recording.mp3> --start <ISO time> --out <directory> --model <zipformer directory> --tagger ced-tiny   # A file, to its end
build/install/radio-worker/bin/radio-worker planned --recordings <directory> --model <zipformer directory> --tagger ced-tiny   # Every capture the backend lists in <directory>/plan.json
build/install/radio-worker/bin/radio-worker sounds <directory> --tagger ced-tiny   # Speech and music scores of a finished recording
docker build -t radio-worker .                   # After installDist; the image holds both models and smoke-tests itself
docker run --rm -v <absolute directory owned by uid 1000>:/data --entrypoint cp radio-worker -r /opt/zipformer /data/   # The model, for runs outside Docker
docker network create radio
docker run -d --rm --network radio --name station -p 127.0.0.1:8770:8770 -v <absolute recording.mp3 path>:/replay.mp3:ro radio-worker station /replay.mp3 --host 0.0.0.0
docker run --rm --network radio -v <absolute directory owned by uid 1000>:/recordings radio-worker run --source http://station:8770/stream.m3u8 --duration <seconds> --out /recordings/<name>
```

## Structure

- `src/main/kotlin/ee/tenman/radio/Recording.kt` - The files the backend reads, as data classes written without null fields, and `round3`, which rounds as Python's `round` does
- `src/main/kotlin/ee/tenman/radio/Station.kt` - Replay station: FFmpeg's HLS muxer behind the JDK's `HttpServer`
- `src/main/kotlin/ee/tenman/radio/Recorder.kt` - Records the stream into parts and owns `status.json`; after a stop, the next part reaches back in the playlist over the time the worker was away
- `src/main/kotlin/ee/tenman/radio/Zipformer.kt` - The streaming Zipformer through sherpa-onnx, cut into utterances at pauses, with word times and sentences
- `src/main/kotlin/ee/tenman/radio/Transcriber.kt` - Live transcript from each part's 16 kHz PCM, journalled in `transcript.jsonl` so a restart resumes it; it writes `sounds.json` before `transcript.json`
- `src/main/kotlin/ee/tenman/radio/Sounds.kt` - Speech and music scores every 0.5 s from the bundled CED-tiny; the backend cuts the songs inside a show from them
- `src/main/kotlin/ee/tenman/radio/Schedule.kt` - Follows the backend's `plan.json`, re-read every minute; it leaves `heartbeat.json` with the time it will next be seen, so the backend can tell a stopped recorder; it records one capture and exits, so every capture starts in a fresh process (#2219)
- `src/main/kotlin/ee/tenman/radio/Main.kt` - The `station`, `run`, `planned` and `sounds` commands, and the shutdown hook that lets SIGTERM finish the part being recorded, for the next start to resume, and the transcript of a recording that has ended
- `ced-tiny/` - CED-tiny int8 from `k2-fsa/sherpa-onnx-ced-tiny-audio-tagging-2024-04-19` (Apache 2.0), its AudioSet labels and its `revision`

## From a fresh clone

`npm run dev` points the backend at the gitignored `radio-data/recordings`. From `radio-worker/`:

```bash
../gradlew :radio-worker:installDist
docker build -t radio-worker .
mkdir -p ../radio-data/model
docker run --rm --user "$(id -u)" -v "$PWD/../radio-data/model:/data" --entrypoint cp radio-worker -r /opt/zipformer /data/
build/install/radio-worker/bin/radio-worker run --duration 600 --programme Kuku --out ../radio-data/recordings/2026-10-18-1200-kuku --model ../radio-data/model/zipformer --tagger ced-tiny
build/install/radio-worker/bin/radio-worker run --source ~/keskpaevatund.mp3 --start 2026-10-17T11:55 --programme Keskpäevatund --out ../radio-data/recordings/2026-10-17-1155-keskpaevatund --model ../radio-data/model/zipformer --tagger ced-tiny
```

The directory name is the recording's name in the Raadio tab: lowercase letters, digits and hyphens. `--start` is the time the recording went on air, in Tallinn time; without it, the start is now. A file is read to its end as fast as FFmpeg can; Kuku live takes `--duration`. Cuts and topics also need `OPENROUTER_API_KEY` in the backend; the transcript shows without it.

From the repository root, the full Docker stack runs the worker as production does, recording every programme ticked „Iga kord“ into the `radio_recordings` volume. The worker's image copies `build/install/radio-worker`, so `installDist` runs before `--build`, as the backend's needs `bootJar`:

```bash
./gradlew bootJar :radio-worker:installDist && SCHEDULING_ENABLED=true docker compose -f docker-compose.local.yml --profile radio up -d --build
```

## Production

The `radio-worker` service in `docker-compose.yml` runs `planned` with the capped CPU, memory and swap measured in #2066, on its own network with no published ports. It sits in the `radio` Compose profile, which the deploy enables only when the repository variable `RADIO_ENABLED` is `true`. The backend's `RadioScheduleJob` imports Kuku's schedule every night and writes `plan.json` into `radio_recordings`: each original airing of a programme ticked „Iga kord“, from 10 s after it starts to 30 s after it ends, with airings up to 10 min apart joined into one recording named `<date>-<HHMM>-<slug>[-<slug>…]` after the first airing's start. The stream lags the broadcast by about 20 s, so that window holds 10 s of broadcast either side of the airing. The worker writes the plan's programme names and air slot into `status.json`. The model is baked into the image; each capture ends the process and the restart policy starts the next. Recordings go to `radio_recordings`, which the backend mounts at `/recordings`. Both images own their mount points as uid 1000, so whichever container creates a volume, both can write to it. The Python worker in `radio-whisper/` is the backup; its AGENTS.md says how to switch to it.

## Rules

- Never write over a recording: FFmpeg runs with `-n`, and an existing directory is reused only when its `status.json` is this worker's recording of the same source
- Place each part by the stream timestamp FFmpeg reports for its first packet, never by the clock
- `transcript.json` and `sounds.json` open with `model` and `revision`, from the `revision` file in the model's directory, because the backend reads only those two fields; the Dockerfile writes Zipformer's from the commit it downloads
- Tests use generated MP3 and PCM, a stub recognizer and the bundled CED-tiny; they never reach Kuku or Hugging Face
- Tests are JUnit 5 with Atrium, named as full sentences in backticks: `` `goes off air after the replay ends` ``
- Run from project root: `npm run test:radio`, which runs Gradle
- CI runs `:radio-worker:test` and builds the image, whose last step is a smoke run
