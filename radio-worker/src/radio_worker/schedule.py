import datetime as dt
import hashlib
import json
import os
import shutil
import time
from collections.abc import Callable
from pathlib import Path
from zoneinfo import ZoneInfo

TALLINN = ZoneInfo("Europe/Tallinn")
PLAN = "plan.json"
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
                attempt(lambda: download(model), f"Downloading {REPOSITORY} into {model}")
            sleep(min(CHECK_SECONDS, capture["start"] - clock()) if capture else CHECK_SECONDS)
            continue
        directory = recordings / capture["name"]
        if ready(model):
            attempt(lambda: say(f"{record(directory, capture['end'] - clock(), capture['programmes'])}: {directory}"), f"Recording {directory}")
        else:
            say(f"Skipping {directory}: {model} holds no verified model")
        pause(capture["end"], clock, sleep)


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
