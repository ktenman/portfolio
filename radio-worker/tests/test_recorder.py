import hashlib
import json
import shutil
import socket
import subprocess
import threading

import pytest
from pytest import approx
from support import broadcast, first_silence, tone, wait

from radio_worker.recorder import KUKU, prepare, read_status, record


@pytest.fixture(scope="module")
def recorded(tmp_path_factory):
    root = tmp_path_factory.mktemp("recorded")
    with broadcast(root) as url:
        prepare(root / "keskpäevatund", url, 12)
        record(root / "keskpäevatund")
    return root / "keskpäevatund"


def probe(path):
    command = ["ffprobe", "-v", "error", "-show_streams", "-of", "json", str(path)]
    return json.loads(subprocess.run(command, capture_output=True, text=True, check=True).stdout)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def dropped(directory, playlist):
    pcm = directory / "raw.pcm"
    return pcm.exists() and pcm.stat().st_size >= 5 * 32000 and "seg_00002.ts" in playlist.read_text()


def test_recorder_copies_the_stream_without_reencoding(recorded):
    stream = probe(recorded / "raw.mp3")["streams"][0]
    assert (stream["codec_name"], stream["sample_rate"], stream["channels"], stream["bit_rate"]) == ("mp3", "48000", 2, "256000")


def test_recorder_records_the_requested_length(recorded):
    assert read_status(recorded)["parts"][0]["durationSeconds"] == approx(12, abs=0.1)


def test_recorder_writes_16_khz_mono_pcm_next_to_the_mp3(recorded):
    assert (recorded / "raw.pcm").stat().st_size / 32000 == approx(12, abs=0.05)


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
        prepare(directory, url, 12)
        shutil.copy(tone(tmp_path / "old.mp3", 3), directory / "raw.mp3")
        (directory / "raw.pcm").write_bytes(bytes(3 * 32000))
        status = read_status(directory)
        status["parts"].append({"file": "raw.mp3", "streamStart": 1.4, "offsetSeconds": 0.0})
        (directory / "status.json").write_text(json.dumps(status))
        before = sha256(directory / "raw.mp3")
        record(directory)
    status = read_status(directory)
    assert ([part["file"] for part in status["parts"]], status["state"], sha256(directory / "raw.mp3")) == (["raw.mp3", "raw-part-002.mp3"], "partial", before)


def test_recorder_places_a_part_after_a_drop_at_its_place_in_the_stream(tmp_path):
    directory = tmp_path / "rec"
    with broadcast(tmp_path, seconds=40) as url:
        prepare(directory, url, 30)
        recording = threading.Thread(target=record, args=(directory,))
        recording.start()
        wait(lambda: dropped(directory, tmp_path / "hls" / "stream.m3u8"))
        subprocess.run(["pkill", "-KILL", "-f", str(directory / "raw.mp3")], check=True)
        recording.join(40)
    part = read_status(directory)["parts"][1]
    assert (part["offsetSeconds"] + first_silence(directory / "raw-part-002.pcm", after=0.5)) % 10 == approx(7.0, abs=0.1)


def test_recorder_refuses_a_directory_that_is_not_its_own(tmp_path):
    (tmp_path / "status.json").write_text('{"state": "captured_needs_review"}')
    with pytest.raises(FileExistsError):
        prepare(tmp_path, KUKU, 4200)


def test_recorder_ends_at_its_end_time_when_the_source_never_answers(tmp_path):
    with socket.socket() as closed:
        closed.bind(("127.0.0.1", 0))
        port = closed.getsockname()[1]
    prepare(tmp_path / "rec", f"http://127.0.0.1:{port}/stream.m3u8", 3)
    recording = threading.Thread(target=record, args=(tmp_path / "rec",))
    recording.start()
    recording.join(10)
    status = read_status(tmp_path / "rec")
    assert (recording.is_alive(), status["state"], status["parts"]) == (False, "failed", [])
