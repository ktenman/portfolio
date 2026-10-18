import contextlib
import hashlib
import json
import subprocess
import threading
import time
from collections.abc import Callable, Iterator
from pathlib import Path
from types import SimpleNamespace

import numpy as np

from radio_worker.recorder import write_status
from radio_worker.station import Station


def tone(path: Path, seconds: float) -> Path:
    wave = "if(between(mod(t,10),7,7.4),0,0.3*sin(2*PI*440*t))"
    source = f"aevalsrc='{wave}|{wave}':s=48000:d={seconds}"
    command = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", source, "-c:a", "libmp3lame", "-b:a", "256k", str(path)]
    subprocess.run(command, check=True)
    return path


@contextlib.contextmanager
def serving(server: Station) -> Iterator[str]:
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        yield f"http://127.0.0.1:{server.server_port}/stream.m3u8"
    finally:
        server.shutdown()
        server.server_close()


def replay(directory: Path, seconds: float = 13, start: float = 0.0) -> Station:
    (directory / "hls").mkdir()
    return Station(tone(directory / "in.mp3", seconds), directory / "hls", port=0, start=start, speed=4, host="127.0.0.1")


@contextlib.contextmanager
def broadcast(directory: Path, seconds: float = 13) -> Iterator[str]:
    with serving(replay(directory, seconds)) as url:
        yield url


def wait(condition: Callable[[], bool], seconds: float = 30) -> None:
    deadline = time.monotonic() + seconds
    while not condition():
        if time.monotonic() > deadline:
            raise TimeoutError(f"condition not met within {seconds} s")
        time.sleep(0.1)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def transcript(directory: Path) -> dict:
    return json.loads((directory / "transcript.json").read_text())


def first_silence(pcm: Path, after: float) -> float:
    samples = np.fromfile(pcm, "<i2").astype(np.float32)
    frames = samples[: len(samples) // 160 * 160].reshape(-1, 160)
    quiet = np.flatnonzero(np.sqrt((frames**2).mean(axis=1)) < 50) / 100
    return float(quiet[quiet > after][0])


def noise(seconds: float, silences: list[float]) -> np.ndarray:
    samples = np.random.default_rng(7).integers(-8000, 8000, int(seconds * 16000), dtype=np.int16)
    for start in silences:
        samples[int(start * 16000) : int((start + 0.3) * 16000)] = 0
    return samples


def write_recording(directory: Path, parts: list[tuple[str, float, np.ndarray, bool]], state: str) -> None:
    for file, _, samples, _ in parts:
        samples.astype("<i2").tofile(directory / Path(file).with_suffix(".pcm"))
    listed = [listing(file, offset, len(samples) / 16000, finished) for file, offset, samples, finished in parts]
    write_status(directory, {"state": state, "source": "http://127.0.0.1:9/stream.m3u8", "startedAt": 0.0, "endsAt": 0.0, "seconds": 0.0, "parts": listed})


def listing(file: str, offset: float, seconds: float, finished: bool) -> dict:
    part = {"file": file, "streamStart": 1.4 + offset, "offsetSeconds": offset}
    return part | {"durationSeconds": seconds, "sha256": "0" * 64, "exitCode": 0} if finished else part


class Stub:
    def __init__(self, fail_on: int | None = None) -> None:
        self.fail_on = fail_on
        self.calls: list[float] = []

    def transcribe(self, audio: np.ndarray, **options: object) -> tuple[Iterator[SimpleNamespace], None]:
        seconds = round(len(audio) / 16000, 3)
        self.calls.append(seconds)
        if len(self.calls) == self.fail_on:
            raise RuntimeError(f"stub fails on call {self.fail_on}")
        word = SimpleNamespace(start=0.0, end=seconds, word=f" lõik{len(self.calls)}", probability=0.9)
        return iter([SimpleNamespace(start=0.0, end=seconds, text=f" lõik {len(self.calls)}", words=[word])]), None
