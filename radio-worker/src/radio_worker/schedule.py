import datetime as dt
import hashlib
import json
import os
import shutil
import threading
import time
from collections.abc import Callable
from pathlib import Path
from zoneinfo import ZoneInfo

from .recorder import hold, prepare, read_status, record, save, write_status

TALLINN = ZoneInfo("Europe/Tallinn")
PLAN = "plan.json"
HEARTBEAT = "heartbeat.json"
CONTINUOUS = "continuous.json"
AROUND_THE_CLOCK = "Pidev salvestus"
CHECK_SECONDS = 60
RETRY_SECONDS = 3600
REPOSITORY = "TalTechNLP/whisper-large-v3-turbo-et-verbatim-2604"
REVISION = "310c550922fc1d1bebf3fad41f9b33ede6652bdc"
MODEL_SHA256 = "e3ebce23a10322e1401e4cafd4e6675811988c0890aff1885469b357a27388da"


def upcoming(recordings: Path, now: float) -> dict | None:
    try:
        captures = json.loads((recordings / PLAN).read_text())
    except (OSError, ValueError):
        return None
    return min((capture for capture in captures if capture["end"] > now), key=lambda capture: capture["start"], default=None)


def ready(model: Path) -> bool:
    return (model / "ct2" / "model.bin").is_file()


def fetch(model: Path) -> None:
    from huggingface_hub import snapshot_download
    download = model / "download"
    snapshot_download(REPOSITORY, revision=REVISION, allow_patterns=["ct2/*"], local_dir=download)
    with open(download / "ct2" / "model.bin", "rb") as weights:
        found = hashlib.file_digest(weights, "sha256").hexdigest()
    if found != MODEL_SHA256:
        shutil.rmtree(download)
        raise ValueError(f"{REPOSITORY} model.bin has SHA-256 {found}, expected {MODEL_SHA256}")
    os.replace(download / "ct2", model / "ct2")
    shutil.rmtree(download)


def planned(
    recordings: Path,
    model: Path,
    record: Callable[[Path, float, list[str]], str],
    download: Callable[[Path], None] = fetch,
    clock: Callable[[], float] = time.time,
    sleep: Callable[[float], None] = time.sleep,
) -> None:
    tried, announced = float("-inf"), None
    while True:
        capture = upcoming(recordings, clock())
        if capture and capture != announced:
            say(f"Next recording {capture['name']} from {shown(capture['start'])} until {shown(capture['end'])}")
            announced = capture
        if capture is None or capture["start"] > clock():
            if not ready(model) and clock() - tried >= RETRY_SECONDS:
                tried = clock()
                beat(recordings, tried, tried + RETRY_SECONDS)
                attempt(lambda: download(model), f"Downloading {REPOSITORY} into {model}")
            wait = min(CHECK_SECONDS, capture["start"] - clock()) if capture else CHECK_SECONDS
            beat(recordings, clock(), clock() + wait)
            sleep(wait)
            continue
        beat(recordings, clock(), capture["end"])
        directory = recordings / capture["name"]
        if ready(model):
            attempt(lambda: say(f"{record(directory, capture['end'] - clock(), capture['programmes'])}: {directory}"), f"Recording {directory}")
        else:
            say(f"Skipping {directory}: {model} holds no verified model")
        pause(capture["end"], clock, sleep)


def around_the_clock(recordings: Path, source: str, clock: Callable[[], float] = time.time, sleep: Callable[[float], None] = time.sleep) -> None:
    while True:
        for directory in unfinished(recordings):
            if not switched_on(recordings) or read_status(directory)["endsAt"] <= clock():
                attempt(lambda: settle(directory, clock()), f"Closing {directory}")
        if not switched_on(recordings):
            sleep(CHECK_SECONDS)
            continue
        try:
            if day(recordings, source, clock) == "failed":
                sleep(CHECK_SECONDS)
        except Exception as error:
            say(f"Recording around the clock failed: {error!r}")
            sleep(CHECK_SECONDS)


def switched_on(recordings: Path) -> bool:
    try:
        return json.loads((recordings / CONTINUOUS).read_text())["on"] is True
    except (OSError, ValueError, KeyError, TypeError):
        return False


def unfinished(recordings: Path) -> list[Path]:
    found = sorted(recordings.glob("*-pidev/status.json"))
    return [status.parent for status in found if read_status(status.parent)["state"] == "recording"]


def day(recordings: Path, source: str, clock: Callable[[], float]) -> str:
    now = clock()
    local = dt.datetime.fromtimestamp(now, TALLINN)
    midnight = (local.replace(hour=0, minute=0, second=0, microsecond=0) + dt.timedelta(days=1)).timestamp()
    directory = next(iter(unfinished(recordings)), recordings / f"{local:%Y-%m-%d-%H%M}-pidev")
    say(f"Recording around the clock into {directory} until {shown(midnight)}")
    prepare(directory, source, midnight - now, [AROUND_THE_CLOCK], continuous=True)
    with hold(directory):
        stop = threading.Event()
        recording = threading.Thread(target=record, args=(directory, stop), daemon=True)
        recording.start()
        while recording.is_alive() and switched_on(recordings):
            recording.join(CHECK_SECONDS)
        stop.set()
        recording.join()
        if read_status(directory)["state"] == "recording":
            close(directory, clock())
    return read_status(directory)["state"]


def settle(directory: Path, now: float) -> None:
    with hold(directory):
        close(directory, now)


def close(directory: Path, now: float) -> None:
    status = read_status(directory)
    ended = min(now, status["endsAt"])
    status.update(endsAt=ended, seconds=round(ended - status["startedAt"], 3))
    write_status(directory, status)
    record(directory, threading.Event())


def beat(recordings: Path, now: float, until: float) -> None:
    save(recordings / HEARTBEAT, json.dumps({"seen": now, "until": until}) + "\n")


def shown(moment: float) -> str:
    return f"{dt.datetime.fromtimestamp(moment, TALLINN):%Y-%m-%d %H:%M}"


def attempt(action: Callable[[], object], what: str) -> None:
    say(what)
    try:
        action()
    except Exception as error:
        say(f"{what} failed: {error!r}")


def pause(until: float, clock: Callable[[], float], sleep: Callable[[float], None]) -> None:
    while (left := until - clock()) > 0:
        sleep(left)


def say(message: str) -> None:
    print(message, flush=True)
