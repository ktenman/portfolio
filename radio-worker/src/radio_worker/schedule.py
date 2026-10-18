import datetime as dt
import hashlib
import os
import shutil
import time
from collections.abc import Callable
from pathlib import Path
from zoneinfo import ZoneInfo

TALLINN = ZoneInfo("Europe/Tallinn")
SATURDAY = 5
OPENS = dt.time(11, 55)
CLOSES = dt.time(13, 5)
RETRY_SECONDS = 3600
REPOSITORY = "TalTechNLP/whisper-large-v3-turbo-et-verbatim-2604"
REVISION = "310c550922fc1d1bebf3fad41f9b33ede6652bdc"
MODEL_SHA256 = "e3ebce23a10322e1401e4cafd4e6675811988c0890aff1885469b357a27388da"


def window(now: float) -> tuple[float, float]:
    day = dt.datetime.fromtimestamp(now, TALLINN).date()
    saturday = day + dt.timedelta(days=(SATURDAY - day.weekday()) % 7)
    start, end = (dt.datetime.combine(saturday, moment, TALLINN).timestamp() for moment in (OPENS, CLOSES))
    return (start, end) if now < end else window(end + 86400)


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


def saturdays(
    recordings: Path,
    model: Path,
    record: Callable[[Path, float], str],
    download: Callable[[Path], None] = fetch,
    clock: Callable[[], float] = time.time,
    sleep: Callable[[float], None] = time.sleep,
) -> None:
    while True:
        start, end = window(clock())
        if not ready(model) and clock() < start:
            attempt(lambda: download(model), f"Downloading {REPOSITORY} into {model}")
            if not ready(model):
                sleep(min(RETRY_SECONDS, start - clock()))
                continue
        directory = recordings / dt.datetime.fromtimestamp(start, TALLINN).date().isoformat()
        say(f"Next recording {directory} until {dt.datetime.fromtimestamp(end, TALLINN):%Y-%m-%d %H:%M}")
        pause(start, clock, sleep)
        if ready(model):
            attempt(lambda: say(f"{record(directory, end - clock())}: {directory}"), f"Recording {directory}")
        else:
            say(f"Skipping {directory}: {model} holds no verified model")
        pause(end, clock, sleep)


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
