import argparse
import contextlib
import datetime as dt
import functools
import math
import signal
import subprocess
import sys
import tempfile
import threading
from collections.abc import Callable
from pathlib import Path
from typing import Any

from radio_worker.recorder import KUKU, RATE, hold, prepare, read_status, record
from radio_worker.schedule import TALLINN, fetch, planned, ready
from radio_worker.station import Station
from radio_worker.transcriber import listen, pcm, transcribe


def main(argv: list[str] | None = None) -> int:
    signal.signal(signal.SIGTERM, signal.default_int_handler)
    parser = build()
    args = parser.parse_args(argv)
    if args.command == "station" and not args.recording.is_file():
        parser.error(f"{args.recording} is not a file")
    if args.command == "station":
        return broadcast(args.recording, args.host, args.port, args.start, args.speed)
    if args.command == "model":
        return download(args.directory)
    if args.command == "sounds":
        return hear(args.directory)
    if args.chunk_seconds < 60:
        parser.error("--chunk-seconds must be at least 60")
    if args.command == "planned":
        return follow(args.recordings, args.model, args.source, args.threads, args.chunk_seconds)
    if not (args.model / "model.bin").is_file():
        parser.error(f"{args.model} holds no CTranslate2 model.bin")
    seconds = span(args.source, args.duration)
    if seconds is None:
        parser.error("--duration is required when --source is a stream")
    load = functools.partial(load_whisper, args.model, args.threads)
    try:
        state = run(args.out, args.source, seconds, load, str(args.model), args.chunk_seconds, args.programmes, args.start)
    except FileExistsError as error:
        print(f"{parser.prog}: error: {error}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print(f"Stopped; run again with --out {args.out} to resume", file=sys.stderr)
        return 130
    print(f"{state}: {args.out / 'transcript.json'}", flush=True)
    return 1 if state == "failed" else 0


def build() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="radio-worker")
    commands = parser.add_subparsers(dest="command", required=True)
    replay = commands.add_parser("station", help="replay a recording as a live HLS stream")
    replay.add_argument("recording", type=Path)
    replay.add_argument("--host", default="127.0.0.1")
    replay.add_argument("--port", type=int, default=8770)
    replay.add_argument("--from", dest="start", type=float, default=0.0, metavar="SECONDS")
    replay.add_argument("--speed", type=float, default=1.0, metavar="FACTOR")
    model = commands.add_parser("model", help="download the pinned TalTech Whisper model into <directory>/ct2")
    model.add_argument("directory", type=Path)
    hearing = commands.add_parser("sounds", help="find speech and music in a recording into <directory>/sounds.json")
    hearing.add_argument("directory", type=Path)
    live = commands.add_parser("run", help="record a stream or an MP3 file and transcribe it while it records")
    live.add_argument("--source", default=KUKU, metavar="URL_OR_FILE")
    live.add_argument("--duration", type=float, metavar="SECONDS", help="required for a stream; a file is recorded to its end")
    live.add_argument("--start", type=moment, metavar="ISO_TIME", help="when the recording went on air, Tallinn time unless an offset is given")
    live.add_argument("--programme", action="append", dest="programmes", metavar="NAME", help="the title in the Raadio tab; repeat for joined programmes")
    live.add_argument("--out", type=Path, required=True, metavar="DIRECTORY")
    live.add_argument("--model", type=Path, required=True, metavar="CT2_DIRECTORY")
    live.add_argument("--threads", type=int, default=2)
    live.add_argument("--chunk-seconds", type=float, default=300, metavar="SECONDS")
    plan = commands.add_parser("planned", help="record and transcribe every capture the backend lists in <recordings>/plan.json")
    plan.add_argument("--recordings", type=Path, default=Path("/recordings"), metavar="DIRECTORY")
    plan.add_argument("--model", type=Path, default=Path("/model"), metavar="DIRECTORY")
    plan.add_argument("--source", default=KUKU)
    plan.add_argument("--threads", type=int, default=2)
    plan.add_argument("--chunk-seconds", type=float, default=300, metavar="SECONDS")
    return parser


def broadcast(recording: Path, host: str, port: int, start: float, speed: float) -> int:
    with tempfile.TemporaryDirectory() as directory, Station(recording, Path(directory), port, start, speed, host) as server:
        print(f"On air at http://{host}:{server.server_port}/stream.m3u8", flush=True)
        with contextlib.suppress(KeyboardInterrupt):
            server.serve_forever()
    return 0


def hear(directory: Path) -> int:
    parts = read_status(directory)["parts"]
    decoded = [decode(directory, part) for part in parts if not pcm(directory, part).exists()]
    try:
        listen(directory, parts)
    finally:
        for path in decoded:
            path.unlink(missing_ok=True)
    return 0


def decode(directory: Path, part: dict) -> Path:
    target = pcm(directory, part)
    subprocess.run(["ffmpeg", "-n", "-v", "error", "-i", str(directory / part["file"]), "-ac", "1", "-ar", str(RATE), "-f", "s16le", str(target)], check=True)
    return target


def download(model: Path) -> int:
    if not ready(model):
        fetch(model)
    print(f"Model ready: {model / 'ct2'}", flush=True)
    return 0


def moment(value: str) -> float:
    start = dt.datetime.fromisoformat(value)
    return (start if start.tzinfo else start.replace(tzinfo=TALLINN)).timestamp()


def span(source: str, duration: float | None) -> float | None:
    if not Path(source).is_file():
        return duration
    probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", source], capture_output=True, text=True, check=True)
    return min(duration or math.inf, float(probe.stdout))


def follow(recordings: Path, model: Path, source: str, threads: int, chunk_seconds: float) -> int:
    weights = model / "ct2"
    load = functools.partial(load_whisper, weights, threads)
    try:
        planned(recordings, model, lambda directory, seconds, programmes, slot: run(directory, source, seconds, load, str(weights), chunk_seconds, programmes, slot=slot))
    except KeyboardInterrupt:
        print("Stopped; the next start resumes an unfinished recording", file=sys.stderr)
    return 130


def run(directory: Path, source: str, seconds: float, load: Callable[[], Any], name: str, chunk_seconds: float, programmes: list[str] | None = None, started: float | None = None, slot: dict | None = None) -> str:
    prepare(directory, source, seconds, programmes, started, slot)
    with hold(directory):
        stop = threading.Event()
        recording = threading.Thread(target=record, args=(directory, stop), daemon=True)
        recording.start()
        try:
            transcribe(directory, load, name, chunk_seconds, recording.is_alive)
        except KeyboardInterrupt:
            stop.set()
            raise
        finally:
            join(recording, stop)
    return read_status(directory)["state"]


def join(recording: threading.Thread, stop: threading.Event) -> None:
    try:
        recording.join()
    except KeyboardInterrupt:
        stop.set()
        recording.join()
        raise


def load_whisper(model: Path, threads: int) -> Any:
    from faster_whisper import WhisperModel
    return WhisperModel(str(model), device="cpu", compute_type="int8", cpu_threads=threads)
