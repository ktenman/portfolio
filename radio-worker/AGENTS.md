# Radio Worker

Python worker that records Kuku's live HLS stream and transcribes it while it records, with the TalTech Whisper model. A replay station serves a saved recording as a live stream, so the whole run can be tested offline (#2064).

## Tech Stack

Python 3.12, uv, faster-whisper 1.2.1 (CTranslate2 int8), numpy, FFmpeg and ffprobe, pytest

## Commands

```bash
uv run pytest                                      # Tests: no network, no model
uv run radio-worker station <recording.mp3>        # Replay at http://127.0.0.1:8770/stream.m3u8
uv run radio-worker run --duration <seconds> --out <directory> --model <ct2 directory>
```

## Structure

- `src/radio_worker/station.py` - Replay station: FFmpeg's HLS muxer behind `http.server`
- `src/radio_worker/recorder.py` - Records the stream into parts and owns `status.json`
- `src/radio_worker/transcriber.py` - Live transcript in chunks cut at pauses, from each part's 16 kHz PCM
- `src/radio_worker/cli.py` - The `station` and `run` commands
- `tests/support.py` - Generated tone, synthetic PCM recordings, a stub model and station helpers shared by the tests

## Rules

- Never write over a recording: FFmpeg runs with `-n`, and an existing directory is reused only for this worker's own unfinished recording
- Place each part by the stream timestamp FFmpeg reports for its first packet, never by the clock
- Tests use generated MP3, synthetic audio and a stub model; they never reach Kuku or Hugging Face
- Test names are full sentences: `test_station_goes_off_air_after_the_replay_ends`
- Run from project root: `npm run test:radio`
