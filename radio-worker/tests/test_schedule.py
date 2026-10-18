import datetime as dt
import hashlib
import json
import threading
import time

import pytest
from support import broadcast, noise, write_recording

from radio_worker import recorder, schedule
from radio_worker.recorder import read_status
from radio_worker.schedule import TALLINN, planned


def at(text: str) -> float:
    return dt.datetime.fromisoformat(text).replace(tzinfo=TALLINN).timestamp()


def shown(moment: float) -> str:
    return dt.datetime.fromtimestamp(moment, TALLINN).isoformat()


class Week:
    def __init__(self, start: str, until: str):
        self.now = at(start)
        self.until = at(until)
        self.calls = []

    def clock(self) -> float:
        return self.now

    def sleep(self, seconds: float) -> None:
        self.now += seconds
        if self.now >= self.until:
            raise KeyboardInterrupt


def model(directory):
    weights = directory / "ct2"
    weights.mkdir(parents=True)
    (weights / "model.bin").write_bytes(b"weights")
    return directory


def plan(recordings, *captures):
    listed = [{"name": name, "programmes": [programme], "start": at(start), "end": at(end)} for name, programme, start, end in captures]
    (recordings / "plan.json").write_text(json.dumps(listed))


SATURDAY = ("2026-10-24-keskpaevatund", "Keskpäevatund", "2026-10-24T11:55", "2026-10-24T13:05")
NEXT_SATURDAY = ("2026-10-31-keskpaevatund", "Keskpäevatund", "2026-10-31T11:55", "2026-10-31T13:05")


def test_planned_records_each_capture_into_its_directory_until_its_end(tmp_path):
    plan(tmp_path, NEXT_SATURDAY, SATURDAY)
    week = Week("2026-10-21T09:00", "2026-10-31T12:00")
    record = lambda directory, seconds, programmes: week.calls.append((directory.name, seconds, programmes, shown(week.now))) or "captured"
    with pytest.raises(KeyboardInterrupt):
        planned(tmp_path, model(tmp_path / "model"), record, clock=week.clock, sleep=week.sleep)
    assert week.calls == [
        ("2026-10-24-keskpaevatund", 4200, ["Keskpäevatund"], "2026-10-24T11:55:00+03:00"),
        ("2026-10-31-keskpaevatund", 4200, ["Keskpäevatund"], "2026-10-31T11:55:00+02:00"),
    ]


def test_planned_started_during_a_capture_records_only_the_rest_of_it(tmp_path):
    plan(tmp_path, SATURDAY)
    week = Week("2026-10-24T12:35", "2026-10-24T14:00")
    record = lambda directory, seconds, programmes: week.calls.append((directory.name, seconds)) or "captured"
    with pytest.raises(KeyboardInterrupt):
        planned(tmp_path, model(tmp_path / "model"), record, clock=week.clock, sleep=week.sleep)
    assert week.calls == [("2026-10-24-keskpaevatund", 1800)]


def test_planned_picks_up_a_capture_added_while_it_waits(tmp_path):
    week = Week("2026-10-24T09:00", "2026-10-24T12:30")
    wait = week.sleep

    def sleep(seconds):
        wait(seconds)
        if week.now >= at("2026-10-24T11:00"):
            plan(tmp_path, SATURDAY)

    record = lambda directory, seconds, programmes: week.calls.append(shown(week.now)) or "captured"
    with pytest.raises(KeyboardInterrupt):
        planned(tmp_path, model(tmp_path / "model"), record, clock=week.clock, sleep=sleep)
    assert week.calls == ["2026-10-24T11:55:00+03:00"]


def test_planned_records_nothing_without_a_plan(tmp_path):
    week = Week("2026-10-24T09:00", "2026-10-25T09:00")
    record = lambda directory, seconds, programmes: week.calls.append(directory.name) or "captured"
    with pytest.raises(KeyboardInterrupt):
        planned(tmp_path, model(tmp_path / "model"), record, clock=week.clock, sleep=week.sleep)
    assert week.calls == []


def test_planned_records_nothing_from_a_plan_it_cannot_read(tmp_path):
    (tmp_path / "plan.json").write_text("{ pooleli")
    week = Week("2026-10-24T09:00", "2026-10-25T09:00")
    record = lambda directory, seconds, programmes: week.calls.append(directory.name) or "captured"
    with pytest.raises(KeyboardInterrupt):
        planned(tmp_path, model(tmp_path / "model"), record, clock=week.clock, sleep=week.sleep)
    assert week.calls == []


def test_planned_waits_for_the_next_capture_after_a_failed_recording(tmp_path):
    plan(tmp_path, SATURDAY, NEXT_SATURDAY)
    week = Week("2026-10-24T11:00", "2026-10-31T12:00")

    def record(directory, seconds, programmes):
        week.calls.append(directory.name)
        raise RuntimeError("Kuku is off air")

    with pytest.raises(KeyboardInterrupt):
        planned(tmp_path, model(tmp_path / "model"), record, clock=week.clock, sleep=week.sleep)
    assert week.calls == ["2026-10-24-keskpaevatund", "2026-10-31-keskpaevatund"]


def test_planned_downloads_a_missing_model_before_the_capture(tmp_path):
    plan(tmp_path, SATURDAY)
    week = Week("2026-10-21T09:00", "2026-10-24T12:00")
    download = lambda directory: week.calls.append(("download", shown(week.now))) or model(directory)
    record = lambda directory, seconds, programmes: week.calls.append(("record", shown(week.now))) or "captured"
    with pytest.raises(KeyboardInterrupt):
        planned(tmp_path, tmp_path / "model", record, download, week.clock, week.sleep)
    assert week.calls == [("download", "2026-10-21T09:00:00+03:00"), ("record", "2026-10-24T11:55:00+03:00")]


def test_planned_never_downloads_the_model_during_a_capture(tmp_path):
    plan(tmp_path, SATURDAY)
    week = Week("2026-10-24T12:00", "2026-10-24T13:30")
    download = lambda directory: week.calls.append(shown(week.now))
    with pytest.raises(KeyboardInterrupt):
        planned(tmp_path, tmp_path / "model", lambda directory, seconds, programmes: "captured", download, week.clock, week.sleep)
    assert week.calls == ["2026-10-24T13:05:00+03:00"]


def test_planned_retries_a_failed_download_within_the_hour(tmp_path):
    week = Week("2026-10-21T09:00", "2026-10-21T11:30")

    def download(directory):
        week.calls.append(shown(week.now))
        raise OSError("Hugging Face is unreachable")

    with pytest.raises(KeyboardInterrupt):
        planned(tmp_path, tmp_path / "model", lambda directory, seconds, programmes: "captured", download, week.clock, week.sleep)
    assert week.calls == ["2026-10-21T09:00:00+03:00", "2026-10-21T10:00:00+03:00", "2026-10-21T11:00:00+03:00"]


def fake_hub(monkeypatch, content: bytes):
    def snapshot_download(repository, revision, allow_patterns, local_dir):
        (local_dir / "ct2").mkdir(parents=True)
        (local_dir / "ct2" / "model.bin").write_bytes(content)
        (local_dir / ".cache").mkdir()

    monkeypatch.setattr("huggingface_hub.snapshot_download", snapshot_download)
    monkeypatch.setattr(schedule, "MODEL_SHA256", hashlib.sha256(b"verified weights").hexdigest())


def heartbeat(recordings):
    return json.loads((recordings / "heartbeat.json").read_text())


def test_planned_leaves_a_heartbeat_until_its_next_check(tmp_path):
    week = Week("2026-10-24T09:00", "2026-10-24T09:00:30")
    with pytest.raises(KeyboardInterrupt):
        planned(tmp_path, model(tmp_path / "model"), lambda *_: "captured", clock=week.clock, sleep=week.sleep)
    assert heartbeat(tmp_path) == {"seen": at("2026-10-24T09:00"), "until": at("2026-10-24T09:01")}


def test_planned_leaves_a_heartbeat_until_the_capture_ends(tmp_path):
    plan(tmp_path, SATURDAY)
    week = Week("2026-10-24T12:35", "2026-10-24T13:10")
    record = lambda directory, seconds, programmes: week.calls.append(heartbeat(tmp_path)) or "captured"
    with pytest.raises(KeyboardInterrupt):
        planned(tmp_path, model(tmp_path / "model"), record, clock=week.clock, sleep=week.sleep)
    assert week.calls == [{"seen": at("2026-10-24T12:35"), "until": at("2026-10-24T13:05")}]


def test_fetch_moves_verified_weights_into_place(tmp_path, monkeypatch):
    fake_hub(monkeypatch, b"verified weights")
    schedule.fetch(tmp_path)
    assert sorted(path.relative_to(tmp_path).as_posix() for path in tmp_path.rglob("*")) == ["ct2", "ct2/model.bin"]


def test_fetch_discards_weights_whose_hash_differs(tmp_path, monkeypatch):
    fake_hub(monkeypatch, b"tampered weights")
    with pytest.raises(ValueError, match="SHA-256"):
        schedule.fetch(tmp_path)
    assert list(tmp_path.iterdir()) == []


def test_around_the_clock_stays_off_until_switched_on(tmp_path):
    assert schedule.switched_on(tmp_path) is False


def test_around_the_clock_reads_the_switch_the_backend_writes(tmp_path):
    (tmp_path / "continuous.json").write_text('{"on": true}')
    assert schedule.switched_on(tmp_path) is True


def test_around_the_clock_records_mp3_parts_without_pcm_until_switched_off(tmp_path, monkeypatch):
    monkeypatch.setattr(schedule, "CHECK_SECONDS", 0.2)
    monkeypatch.setattr(recorder, "PART_SECONDS", 3)
    recordings = tmp_path / "recordings"
    recordings.mkdir()
    (recordings / "continuous.json").write_text('{"on": true}')
    with broadcast(tmp_path, 20) as url:
        threading.Timer(6, lambda: (recordings / "continuous.json").write_text('{"on": false}')).start()
        schedule.day(recordings, url, time.time)
    directory = next(recordings.glob("*-pidev"))
    status = read_status(directory)
    found = (status["state"] != "recording", status["continuous"], len(status["parts"]) > 1, list(directory.glob("*.pcm")))
    assert found == (True, True, True, [])


def test_around_the_clock_closes_a_recording_a_restart_left_unfinished(tmp_path):
    directory = tmp_path / "2026-10-18-0000-pidev"
    directory.mkdir()
    write_recording(directory, [("raw.mp3", 0.0, noise(4, []), True)], "recording")
    schedule.settle(directory, time.time())
    assert read_status(directory)["state"] == "captured"
