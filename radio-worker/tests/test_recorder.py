import json
import shutil
import socket
import subprocess
import threading
from pathlib import Path

import pytest
from pytest import approx
from support import broadcast, first_silence, sha256, tone, wait

from radio_worker.recorder import KUKU, RATE, hold, prepare, read_status, record, register, stems, write_status
from radio_worker.transcriber import seconds


@pytest.fixture(scope="module")
def recorded(tmp_path_factory: pytest.TempPathFactory) -> Path:
    root = tmp_path_factory.mktemp("recorded")
    with broadcast(root) as url:
        prepare(root / "keskpäevatund", url, 12)
        record(root / "keskpäevatund", threading.Event())
    return root / "keskpäevatund"


def caught_up(directory: Path, playlist: Path) -> bool:
    listed = playlist.read_text().count(".ts") if playlist.exists() else 0
    return listed >= 4 and seconds(directory / "raw.pcm") >= 6 * listed - 6.5


def test_recorder_copies_the_stream_without_reencoding(recorded):
    stream = json.loads((recorded / "raw.probe.json").read_text())["streams"][0]
    assert (stream["codec_name"], stream["sample_rate"], stream["channels"], stream["bit_rate"]) == ("mp3", "48000", 2, "256000")


def test_recorder_records_the_requested_length(recorded):
    assert read_status(recorded)["parts"][0]["durationSeconds"] == approx(12, abs=0.1)


def test_recorder_writes_16_khz_mono_pcm_next_to_the_mp3(recorded):
    assert seconds(recorded / "raw.pcm") == approx(12, abs=0.05)


def test_recorder_stores_the_sha256_of_each_part(recorded):
    assert read_status(recorded)["parts"][0]["sha256"] == sha256(recorded / "raw.mp3")


def test_recorder_marks_a_complete_recording_captured(recorded):
    assert read_status(recorded)["state"] == "captured"


def test_recorder_logs_when_each_part_starts_and_ends(recorded):
    lines = (recorded / "events.log").read_text().splitlines()
    assert [line.split(" ")[1:3] for line in lines] == [["raw.mp3", "started"], ["raw.mp3", "ended"]]


def test_recorder_carries_on_into_a_new_part_after_a_restart(tmp_path):
    directory = tmp_path / "rec"
    with broadcast(tmp_path) as url:
        prepare(directory, url, 4)
        shutil.copy(tone(tmp_path / "old.mp3", 3), directory / "raw.mp3")
        (directory / "raw.pcm").write_bytes(bytes(3 * 2 * RATE))
        status = read_status(directory)
        status["parts"].append({"file": "raw.mp3", "streamStart": 1.4, "offsetSeconds": 0.0})
        write_status(directory, status)
        before = sha256(directory / "raw.mp3")
        record(directory, threading.Event())
    status = read_status(directory)
    assert ([part["file"] for part in status["parts"]], status["state"], sha256(directory / "raw.mp3")) == (["raw.mp3", "raw-part-002.mp3"], "partial", before)


def test_recorder_places_a_part_after_a_drop_at_its_place_in_the_stream(tmp_path):
    directory = tmp_path / "rec"
    with broadcast(tmp_path, seconds=60) as url:
        prepare(directory, url, 40)
        recording = threading.Thread(target=record, args=(directory, threading.Event()))
        recording.start()
        wait(lambda: caught_up(directory, tmp_path / "hls" / "stream.m3u8"))
        subprocess.run(["pkill", "-KILL", "-f", str(directory / "raw.mp3")], check=True)
        recording.join(40)
    first, second = read_status(directory)["parts"][:2]
    silence = second["offsetSeconds"] + first_silence(directory / "raw-part-002.pcm", after=0.5)
    assert (second["offsetSeconds"] < first["durationSeconds"], silence % 10) == (True, approx(7.0, abs=0.1))


def offsets(directory: Path, starts: list[str]) -> list[float]:
    status = {"parts": []}
    for stem, start in zip(stems(), starts):
        (directory / f"{stem}.mp3").write_bytes(b"")
        (directory / f"{stem}.log").write_text(f"  Duration: N/A, start: {start}, bitrate: N/A\n")
        register(directory, status, stem)
    return [part["offsetSeconds"] for part in status["parts"]]


def test_recorder_places_parts_across_the_timestamp_wrap(tmp_path):
    assert offsets(tmp_path, ["95413.694667", "5.976978"]) == [0.0, 36.0]


def test_recorder_places_a_part_that_starts_before_the_first_one(tmp_path):
    assert offsets(tmp_path, ["100.000000", "94.000000"]) == [0.0, -6.0]


def test_recorder_adopts_a_part_it_never_registered(tmp_path):
    directory = tmp_path / "rec"
    with broadcast(tmp_path) as url:
        prepare(directory, url, 4)
        shutil.copy(tone(tmp_path / "old.mp3", 3), directory / "raw.mp3")
        (directory / "raw.log").write_text("  Duration: N/A, start: 1.400000, bitrate: N/A\n")
        record(directory, threading.Event())
    assert [part["file"] for part in read_status(directory)["parts"]] == ["raw.mp3", "raw-part-002.mp3"]


def test_recorder_refuses_a_directory_another_worker_holds(tmp_path):
    prepare(tmp_path / "rec", KUKU, 4200)
    with hold(tmp_path / "rec"), pytest.raises(FileExistsError, match="another worker"), hold(tmp_path / "rec"):
        pass


def test_recorder_refuses_a_directory_that_is_not_its_own(tmp_path):
    (tmp_path / "status.json").write_text('{"state": "captured_needs_review"}')
    with pytest.raises(FileExistsError):
        prepare(tmp_path, KUKU, 4200)


def test_recorder_ends_at_its_end_time_when_the_source_never_answers(tmp_path):
    with socket.socket() as closed:
        closed.bind(("127.0.0.1", 0))
        port = closed.getsockname()[1]
    prepare(tmp_path / "rec", f"http://127.0.0.1:{port}/stream.m3u8", 3)
    recording = threading.Thread(target=record, args=(tmp_path / "rec", threading.Event()))
    recording.start()
    recording.join(10)
    status = read_status(tmp_path / "rec")
    assert (recording.is_alive(), status["state"], status["parts"]) == (False, "failed", [])
