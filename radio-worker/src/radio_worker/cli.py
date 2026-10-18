import argparse
import contextlib
import functools
import signal
import sys
import tempfile
import threading
from collections.abc import Callable
from pathlib import Path
from typing import Any

from radio_worker.recorder import KUKU, prepare, read_status, record
from radio_worker.station import Station
from radio_worker.transcriber import transcribe


def main(argv: list[str] | None = None) -> int:
    signal.signal(signal.SIGTERM, signal.default_int_handler)
    parser = build()
    args = parser.parse_args(argv)
    if args.command == "station" and not args.recording.is_file():
        parser.error(f"{args.recording} is not a file")
    if args.command == "station":
        return broadcast(args.recording, args.host, args.port, args.start, args.speed)
    if args.chunk_seconds < 60:
        parser.error("--chunk-seconds must be at least 60")
    if not (args.model / "model.bin").is_file():
        parser.error(f"{args.model} holds no CTranslate2 model.bin")
    load = functools.partial(load_whisper, args.model, args.threads)
    try:
        state = run(args.out, args.source, args.duration, load, str(args.model), args.chunk_seconds)
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
    live = commands.add_parser("run", help="record a stream and transcribe it while it records")
    live.add_argument("--source", default=KUKU)
    live.add_argument("--duration", type=float, required=True, metavar="SECONDS")
    live.add_argument("--out", type=Path, required=True, metavar="DIRECTORY")
    live.add_argument("--model", type=Path, required=True, metavar="CT2_DIRECTORY")
    live.add_argument("--threads", type=int, default=2)
    live.add_argument("--chunk-seconds", type=float, default=300, metavar="SECONDS")
    return parser


def broadcast(recording: Path, host: str, port: int, start: float, speed: float) -> int:
    with tempfile.TemporaryDirectory() as directory:
        server = Station(recording, Path(directory), port, start, speed, host)
        print(f"On air at http://{host}:{server.server_port}/stream.m3u8", flush=True)
        with contextlib.suppress(KeyboardInterrupt):
            server.serve_forever()
        server.server_close()
    return 0


def run(directory: Path, source: str, seconds: float, load: Callable[[], Any], name: str, chunk_seconds: float) -> str:
    prepare(directory, source, seconds)
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
