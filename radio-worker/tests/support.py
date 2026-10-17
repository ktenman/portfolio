import contextlib
import subprocess
import threading
from collections.abc import Iterator
from pathlib import Path

from radio_worker.station import Station, station


def tone(path: Path, seconds: float) -> Path:
    wave = "if(between(mod(t,12),9,9.4),0,0.3*sin(2*PI*440*t))"
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
