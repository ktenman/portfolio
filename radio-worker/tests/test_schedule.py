import datetime as dt
import hashlib

import pytest

from radio_worker import schedule
from radio_worker.schedule import TALLINN, saturdays, window


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


def test_window_on_a_wednesday_is_the_coming_saturday_show():
    start, end = window(at("2026-10-21T09:00"))
    assert (shown(start), shown(end)) == ("2026-10-24T11:55:00+03:00", "2026-10-24T13:05:00+03:00")


def test_window_during_the_show_is_the_show_on_air():
    assert window(at("2026-10-24T12:30")) == (at("2026-10-24T11:55"), at("2026-10-24T13:05"))


def test_window_after_the_show_is_the_next_saturday_in_winter_time():
    start, _ = window(at("2026-10-24T13:05"))
    assert shown(start) == "2026-10-31T11:55:00+02:00"


def test_saturdays_records_each_show_into_its_dated_directory_until_its_end(tmp_path):
    week = Week("2026-10-21T09:00", "2026-10-31T12:00")
    record = lambda directory, seconds: week.calls.append((directory.name, seconds, shown(week.now))) or "captured"
    with pytest.raises(KeyboardInterrupt):
        saturdays(tmp_path, model(tmp_path / "model"), record, clock=week.clock, sleep=week.sleep)
    assert week.calls == [("2026-10-24", 4200, "2026-10-24T11:55:00+03:00"), ("2026-10-31", 4200, "2026-10-31T11:55:00+02:00")]


def test_saturdays_started_during_the_show_records_only_the_rest_of_it(tmp_path):
    week = Week("2026-10-24T12:35", "2026-10-24T14:00")
    record = lambda directory, seconds: week.calls.append((directory.name, seconds)) or "captured"
    with pytest.raises(KeyboardInterrupt):
        saturdays(tmp_path, model(tmp_path / "model"), record, clock=week.clock, sleep=week.sleep)
    assert week.calls == [("2026-10-24", 1800)]


def test_saturdays_waits_for_the_next_show_after_a_failed_recording(tmp_path):
    week = Week("2026-10-24T11:00", "2026-10-31T12:00")

    def record(directory, seconds):
        week.calls.append(directory.name)
        raise RuntimeError("Kuku is off air")

    with pytest.raises(KeyboardInterrupt):
        saturdays(tmp_path, model(tmp_path / "model"), record, clock=week.clock, sleep=week.sleep)
    assert week.calls == ["2026-10-24", "2026-10-31"]


def test_saturdays_downloads_a_missing_model_before_the_show(tmp_path):
    week = Week("2026-10-21T09:00", "2026-10-24T12:00")
    download = lambda directory: week.calls.append(("download", shown(week.now))) or model(directory)
    record = lambda directory, seconds: week.calls.append(("record", shown(week.now))) or "captured"
    with pytest.raises(KeyboardInterrupt):
        saturdays(tmp_path, tmp_path / "model", record, download, week.clock, week.sleep)
    assert week.calls == [("download", "2026-10-21T09:00:00+03:00"), ("record", "2026-10-24T11:55:00+03:00")]


def test_saturdays_never_downloads_the_model_during_the_show(tmp_path):
    week = Week("2026-10-24T12:00", "2026-10-24T13:30")
    download = lambda directory: week.calls.append(shown(week.now))
    with pytest.raises(KeyboardInterrupt):
        saturdays(tmp_path, tmp_path / "model", lambda directory, seconds: "captured", download, week.clock, week.sleep)
    assert week.calls == ["2026-10-24T13:05:00+03:00"]


def test_saturdays_retries_a_failed_download_within_the_hour(tmp_path):
    week = Week("2026-10-21T09:00", "2026-10-21T11:30")

    def download(directory):
        week.calls.append(shown(week.now))
        raise OSError("Hugging Face is unreachable")

    with pytest.raises(KeyboardInterrupt):
        saturdays(tmp_path, tmp_path / "model", lambda directory, seconds: "captured", download, week.clock, week.sleep)
    assert week.calls == ["2026-10-21T09:00:00+03:00", "2026-10-21T10:00:00+03:00", "2026-10-21T11:00:00+03:00"]


def fake_hub(monkeypatch, content: bytes):
    def snapshot_download(repository, revision, allow_patterns, local_dir):
        (local_dir / "ct2").mkdir(parents=True)
        (local_dir / "ct2" / "model.bin").write_bytes(content)
        (local_dir / ".cache").mkdir()

    monkeypatch.setattr("huggingface_hub.snapshot_download", snapshot_download)
    monkeypatch.setattr(schedule, "MODEL_SHA256", hashlib.sha256(b"verified weights").hexdigest())


def test_fetch_moves_verified_weights_into_place(tmp_path, monkeypatch):
    fake_hub(monkeypatch, b"verified weights")
    schedule.fetch(tmp_path)
    assert sorted(path.relative_to(tmp_path).as_posix() for path in tmp_path.rglob("*")) == ["ct2", "ct2/model.bin"]


def test_fetch_discards_weights_whose_hash_differs(tmp_path, monkeypatch):
    fake_hub(monkeypatch, b"tampered weights")
    with pytest.raises(ValueError, match="SHA-256"):
        schedule.fetch(tmp_path)
    assert list(tmp_path.iterdir()) == []
