import json
import math
import time
from collections.abc import Callable
from pathlib import Path
from typing import Any

import numpy as np

from radio_worker.recorder import RATE, read_status, save

PAUSE = 0.3
LOOKBACK = 20
SHORTEST = 0.1
POLL_SECONDS = 1
OPTIONS = {"language": "et", "beam_size": 3, "condition_on_previous_text": False, "vad_filter": False, "word_timestamps": True}


def pause_before(window: np.ndarray, start: float) -> float:
    hop = RATE // 100
    frames = window[: len(window) // hop * hop].reshape(-1, hop)
    db = 20 * np.log10(np.maximum(np.sqrt((frames**2).mean(axis=1)), 1e-6))
    width = int(PAUSE * 100)
    means = np.convolve(db, np.ones(width) / width, mode="valid")
    return start + (int(np.argmin(means)) + width / 2) / 100


def transcribe(directory: Path, load: Callable[[], Any], name: str, chunk_seconds: float, recording: Callable[[], bool]) -> None:
    if (directory / "transcript.json").exists():
        return
    session = Session(directory, load(), chunk_seconds)
    index = 0
    while True:
        alive = recording()
        status = read_status(directory)
        over = status["state"] != "recording"
        if not (over or alive):
            raise RuntimeError(f"The recorder stopped while {directory / 'status.json'} still says recording")
        if index == len(status["parts"]) and over:
            break
        if index < len(status["parts"]) and session.advance(status["parts"], index, over):
            index += 1
            continue
        time.sleep(POLL_SECONDS)
    publish(directory, status, session.chunks, name, chunk_seconds)


class Session:
    def __init__(self, directory: Path, model: Any, chunk_seconds: float) -> None:
        self.directory = directory
        self.model = model
        self.chunk_seconds = chunk_seconds
        self.chunks = resume(directory / "transcript.jsonl")

    def advance(self, parts: list[dict], index: int, over: bool) -> bool:
        part = parts[index]
        done = [chunk for chunk in self.chunks if chunk["part"] == part["file"]]
        if done and done[-1]["final"]:
            return True
        finished = over or "durationSeconds" in part
        length = seconds(pcm(self.directory, part))
        cut = done[-1]["to"] if done else overlap(self.directory, parts, index)
        while (mark := (math.floor((cut + LOOKBACK) / self.chunk_seconds) + 1) * self.chunk_seconds) <= length:
            pause = pause_before(read(pcm(self.directory, part), mark - LOOKBACK, mark), mark - LOOKBACK)
            cut = self.append(part, cut, pause, False)
        if finished:
            self.append(part, cut, max(cut, length), True)
        return finished

    def append(self, part: dict, start: float, end: float, final: bool) -> float:
        segments = whisper(self.model, read(pcm(self.directory, part), start, end), part["offsetSeconds"] + start) if end - start >= SHORTEST else []
        chunk = {"part": part["file"], "from": round(start, 3), "to": round(end, 3), "final": final, "segments": segments}
        with (self.directory / "transcript.jsonl").open("a", encoding="utf-8") as file:
            file.write(json.dumps(chunk, ensure_ascii=False) + "\n")
        self.chunks.append(chunk)
        return chunk["to"]


def resume(path: Path) -> list[dict]:
    if not path.exists():
        return []
    chunks = []
    for line in path.read_text(encoding="utf-8", errors="replace").split("\n"):
        try:
            chunks.append(json.loads(line))
        except json.JSONDecodeError:
            break
    save(path, "".join(json.dumps(chunk, ensure_ascii=False) + "\n" for chunk in chunks))
    return chunks


def pcm(directory: Path, part: dict) -> Path:
    return directory / Path(part["file"]).with_suffix(".pcm")


def seconds(path: Path) -> float:
    return path.stat().st_size // 2 / RATE if path.exists() else 0.0


def overlap(directory: Path, parts: list[dict], index: int) -> float:
    ends = [part["offsetSeconds"] + seconds(pcm(directory, part)) for part in parts[:index]]
    return max([0.0] + [end - parts[index]["offsetSeconds"] for end in ends])


def read(path: Path, start: float, end: float) -> np.ndarray:
    first = int(start * RATE)
    return np.fromfile(path, "<i2", int(end * RATE) - first, offset=first * 2).astype(np.float32) / 32768


def whisper(model: Any, audio: np.ndarray, origin: float) -> list[dict]:
    segments, _ = model.transcribe(audio, **OPTIONS)
    return [
        {
            "start": round(origin + segment.start, 3),
            "end": round(origin + segment.end, 3),
            "text": segment.text.strip(),
            "words": [{"start": round(origin + word.start, 3), "end": round(origin + word.end, 3), "word": word.word, "probability": round(word.probability, 3)} for word in segment.words],
        }
        for segment in segments
    ]


def publish(directory: Path, status: dict, chunks: list[dict], name: str, chunk_seconds: float) -> None:
    offsets = {part["file"]: part["offsetSeconds"] for part in status["parts"]}
    segments = [segment for chunk in chunks for segment in chunk["segments"]]
    (directory / "transcript.txt").write_text("".join(f"{segment['text']}\n" for segment in segments), encoding="utf-8")
    (directory / "transcript.srt").write_text("".join(cue(number, segment) for number, segment in enumerate(segments, 1)), encoding="utf-8")
    transcript = {
        "model": name,
        "options": OPTIONS,
        "chunkSeconds": chunk_seconds,
        "parts": [{"file": part["file"], "offsetSeconds": part["offsetSeconds"], "sha256": part.get("sha256")} for part in status["parts"]],
        "chunks": [{"start": round(offsets[chunk["part"]] + chunk["from"], 3), "end": round(offsets[chunk["part"]] + chunk["to"], 3)} for chunk in chunks],
        "segments": [{"start": segment["start"], "end": segment["end"], "text": segment["text"]} for segment in segments],
        "words": [word for segment in segments for word in segment["words"]],
    }
    save(directory / "transcript.json", json.dumps(transcript, ensure_ascii=False, indent=2) + "\n")


def cue(number: int, segment: dict) -> str:
    return f"{number}\n{timestamp(segment['start'])} --> {timestamp(segment['end'])}\n{segment['text']}\n\n"


def timestamp(value: float) -> str:
    hours, rest = divmod(round(value * 1000), 3_600_000)
    minutes, rest = divmod(rest, 60_000)
    return f"{hours:02d}:{minutes:02d}:{rest // 1000:02d},{rest % 1000:03d}"
