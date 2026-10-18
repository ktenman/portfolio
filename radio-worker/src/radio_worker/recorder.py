import contextlib
import datetime
import fcntl
import hashlib
import itertools
import json
import math
import os
import re
import subprocess
import threading
import time
from collections.abc import Iterator
from pathlib import Path

KUKU = "https://router.euddn.net/189e8110a165b526b8b767d23e4ebb55/kuku/stream.m3u8?c=8000"
STATES = ("recording", "captured", "partial", "failed")
START = re.compile(rb"start: (-?\d+\.\d+)")
RATE = 16000
WRAP = 2**33 / 90000
POLL_SECONDS = 0.5
RETRY_SECONDS = 2


def prepare(directory: Path, source: str, seconds: float, programmes: list[str] | None = None) -> None:
    if directory.exists():
        claim(directory, source)
        return
    directory.mkdir(parents=True)
    now = time.time()
    named = {"programmes": programmes} if programmes else {}
    write_status(directory, {"state": "recording", "source": source, "startedAt": round(now, 3), "endsAt": round(now + seconds, 3), "seconds": float(seconds), "parts": [], **named})


def claim(directory: Path, source: str) -> None:
    status = read_status(directory) if (directory / "status.json").exists() else {}
    if status.get("state") not in STATES:
        raise FileExistsError(f"{directory} exists and holds no recording made by this worker")
    if status["source"] != source:
        raise FileExistsError(f"{directory} holds a recording of {status['source']}, not of {source}")


@contextlib.contextmanager
def hold(directory: Path) -> Iterator[None]:
    descriptor = os.open(directory, os.O_RDONLY)
    try:
        fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError as error:
        os.close(descriptor)
        raise FileExistsError(f"{directory} is being recorded by another worker") from error
    try:
        yield
    finally:
        os.close(descriptor)


def read_status(directory: Path) -> dict:
    return json.loads((directory / "status.json").read_text())


def write_status(directory: Path, status: dict) -> None:
    save(directory / "status.json", json.dumps(status, indent=2) + "\n")


def save(path: Path, text: str) -> None:
    temporary = path.with_name(f"{path.name}.tmp")
    temporary.write_text(text, encoding="utf-8")
    temporary.replace(path)


def record(directory: Path, stop: threading.Event) -> None:
    status = read_status(directory)
    if status["state"] != "recording":
        return
    try:
        adopt(directory, status)
        for part in status["parts"]:
            if "durationSeconds" not in part:
                finish(directory, status, part, None)
        capture(directory, status, stop)
    except BaseException:
        status["state"] = "failed"
        write_status(directory, status)
        raise
    if stop.is_set():
        return
    status["state"] = verdict(status)
    write_status(directory, status)


def adopt(directory: Path, status: dict) -> None:
    listed = {part["file"] for part in status["parts"]}
    for stem in itertools.takewhile(lambda stem: (directory / f"{stem}.mp3").exists(), stems()):
        if f"{stem}.mp3" not in listed:
            register(directory, status, stem)


def capture(directory: Path, status: dict, stop: threading.Event) -> None:
    while (remaining := round(status["endsAt"] - time.time(), 3)) > 0 and not stop.is_set():
        part = attempt(directory, status, remaining, stop)
        if part is not None and part["durationSeconds"] >= remaining - 1:
            return
        stop.wait(RETRY_SECONDS)


def attempt(directory: Path, status: dict, remaining: float, stop: threading.Event) -> dict | None:
    stem = next_stem(directory)
    with (directory / f"{stem}.log").open("ab") as log:
        process = subprocess.Popen(command(status["source"], remaining, directory / stem), stderr=log)
    part = None
    try:
        while process.poll() is None and not stop.wait(POLL_SECONDS):
            part = part or register(directory, status, stem)
    finally:
        process.kill()
        process.wait()
    part = part or register(directory, status, stem)
    if part is None and (directory / f"{stem}.mp3").exists():
        raise RuntimeError(f"FFmpeg wrote {stem}.mp3 but {stem}.log does not say where the stream starts")
    if part is None:
        return None
    finish(directory, status, part, process.returncode)
    return part


def stems() -> Iterator[str]:
    return ("raw" if index == 1 else f"raw-part-{index:03d}" for index in itertools.count(1))


def next_stem(directory: Path) -> str:
    return next(stem for stem in stems() if not (directory / f"{stem}.mp3").exists())


def command(source: str, remaining: float, base: Path) -> list[str]:
    return [
        "ffmpeg", "-hide_banner", "-nostdin", "-nostats", "-loglevel", "info", "-n",
        "-rw_timeout", "15000000", "-t", f"{remaining:.3f}", "-i", source,
        "-map", "0:a:0", "-c:a", "copy", "-flush_packets", "1", f"{base}.mp3",
        "-map", "0:a:0", "-ac", "1", "-ar", str(RATE), "-f", "s16le", "-flush_packets", "1", f"{base}.pcm",
    ]


def register(directory: Path, status: dict, stem: str) -> dict | None:
    if not (directory / f"{stem}.mp3").exists():
        return None
    starts = START.findall((directory / f"{stem}.log").read_bytes())
    if not starts:
        return None
    start = float(starts[-1])
    origin = status["parts"][0]["streamStart"] if status["parts"] else start
    part = {"file": f"{stem}.mp3", "streamStart": start, "offsetSeconds": round(math.remainder(start - origin, WRAP), 3)}
    status["parts"].append(part)
    write_status(directory, status)
    event(directory, f"{part['file']} started at {part['offsetSeconds']} s")
    return part


def finish(directory: Path, status: dict, part: dict, code: int | None) -> None:
    mp3 = directory / part["file"]
    probe = subprocess.run(["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", str(mp3)], capture_output=True, text=True).stdout
    mp3.with_suffix(".probe.json").write_text(probe)
    with mp3.open("rb") as file:
        digest = hashlib.file_digest(file, "sha256").hexdigest()
    duration = float(json.loads(probe or "{}").get("format", {}).get("duration", 0.0))
    part.update(durationSeconds=round(duration, 3), sha256=digest, exitCode=code)
    write_status(directory, status)
    event(directory, f"{part['file']} ended after {part['durationSeconds']} s, exit {code}")


def verdict(status: dict) -> str:
    parts = status["parts"]
    if not parts:
        return "failed"
    if len(parts) == 1 and parts[0]["durationSeconds"] >= status["seconds"] - 1:
        return "captured"
    return "partial"


def event(directory: Path, message: str) -> None:
    stamp = datetime.datetime.now(datetime.UTC).isoformat(timespec="seconds")
    with (directory / "events.log").open("a") as log:
        log.write(f"{stamp} {message}\n")
