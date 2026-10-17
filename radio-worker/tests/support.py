import contextlib
import subprocess
import threading
import time
from collections.abc import Callable, Iterator
from pathlib import Path

import numpy as np

from radio_worker.station import Station, station


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


@contextlib.contextmanager
def broadcast(directory: Path, seconds: float = 13, speed: float = 4) -> Iterator[str]:
    with serving(station(tone(directory / "in.mp3", seconds), directory / "hls", port=0, speed=speed)) as url:
        yield url


def wait(condition: Callable[[], bool], seconds: float = 30) -> None:
    deadline = time.monotonic() + seconds
    while not condition():
        if time.monotonic() > deadline:
            raise TimeoutError(f"condition not met within {seconds} s")
        time.sleep(0.1)


def first_silence(pcm: Path, after: float) -> float:
    samples = np.fromfile(pcm, "<i2").astype(np.float32)
    frames = samples[: len(samples) // 160 * 160].reshape(-1, 160)
    quiet = np.flatnonzero(np.sqrt((frames**2).mean(axis=1)) < 50) / 100
    return float(quiet[quiet > after][0])
