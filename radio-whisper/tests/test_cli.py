import json
import os
import signal
import threading
import time

import pytest
from support import Stub, broadcast, listing, noise, sha256, tone, transcript, wait, write_recording

from radio_worker.cli import main, moment, run, span
from radio_worker.recorder import read_status, write_status


def test_run_records_and_transcribes_a_replay(tmp_path):
    with broadcast(tmp_path) as url:
        state = run(tmp_path / "rec", url, 4, Stub, "stub", 60)
    assert (state, len(transcript(tmp_path / "rec")["words"])) == ("captured", 1)


def test_run_records_a_file_once_to_its_end_at_the_time_it_went_on_air(tmp_path):
    source = str(tone(tmp_path / "in.mp3", 13))
    state = run(tmp_path / "rec", source, span(source, 60), Stub, "stub", 60, started=moment("2026-10-17T11:55"))
    status = read_status(tmp_path / "rec")
    assert (state, len(status["parts"]), status["startedAt"]) == ("captured", 1, 1792227300.0)


def test_a_stream_is_recorded_for_the_duration_it_is_given(tmp_path):
    assert span("http://station:8770/stream.m3u8", 600) == 600


def test_model_leaves_a_downloaded_model_in_place(tmp_path):
    (tmp_path / "ct2").mkdir()
    (tmp_path / "ct2" / "model.bin").write_bytes(b"weights")
    assert main(["model", str(tmp_path)]) == 0


def test_run_only_finishes_the_transcript_of_an_ended_recording(tmp_path):
    directory = tmp_path / "rec"
    with broadcast(tmp_path) as url:
        run(directory, url, 4, Stub, "stub", 60)
        before = sha256(directory / "raw.mp3")
        (directory / "transcript.json").unlink()
        (directory / "transcript.jsonl").unlink()
        write_status(directory, read_status(directory) | {"endsAt": time.time() + 60})
        run(directory, url, 4, Stub, "stub", 60)
    after = sha256(directory / "raw.mp3")
    assert ([part["file"] for part in read_status(directory)["parts"]], after, (directory / "transcript.json").exists()) == (["raw.mp3"], before, True)


def test_run_stops_the_recording_when_interrupted(tmp_path):
    directory = tmp_path / "rec"
    def interrupt():
        wait(lambda: (directory / "raw.mp3").exists())
        raise KeyboardInterrupt
    with broadcast(tmp_path, seconds=40) as url, pytest.raises(KeyboardInterrupt):
        run(directory, url, 30, interrupt, "stub", 60)
    status = read_status(directory)
    assert (status["state"], status["parts"][0]["durationSeconds"] < 20) == ("recording", True)


def test_run_stops_the_recording_when_interrupted_after_a_transcriber_error(tmp_path, request):
    directory = tmp_path / "rec"
    signal.signal(signal.SIGINT, signal.default_int_handler)
    timer = threading.Timer(0.5, os.kill, (os.getpid(), signal.SIGINT))
    request.addfinalizer(timer.cancel)
    def broken():
        wait(lambda: (directory / "raw.mp3").exists())
        timer.start()
        raise RuntimeError("model files are damaged")
    with broadcast(tmp_path, seconds=40) as url, pytest.raises(KeyboardInterrupt):
        run(directory, url, 30, broken, "stub", 60)
    status = read_status(directory)
    assert (status["state"], status["parts"][0]["durationSeconds"] < 20) == ("recording", True)


def test_station_command_stops_cleanly_on_sigterm(tmp_path, request):
    (tmp_path / "in.mp3").write_bytes(b"")
    signal.signal(signal.SIGTERM, lambda *_: pytest.fail("SIGTERM did not take the Ctrl-C path"))
    timer = threading.Timer(1, os.kill, (os.getpid(), signal.SIGTERM))
    request.addfinalizer(timer.cancel)
    timer.start()
    assert main(["station", str(tmp_path / "in.mp3"), "--port", "0"]) == 0


def test_run_command_refuses_a_directory_that_is_not_a_recording(tmp_path, capsys):
    (tmp_path / "model").mkdir()
    (tmp_path / "model" / "model.bin").write_bytes(b"")
    (tmp_path / "status.json").write_text('{"state": "captured_needs_review"}')
    code = main(["run", "--duration", "4200", "--out", str(tmp_path), "--model", str(tmp_path / "model")])
    assert (code, "holds no recording made by this worker" in capsys.readouterr().err) == (2, True)


def test_sounds_hears_a_recording_already_transcribed(tmp_path):
    write_recording(tmp_path, [("raw.mp3", 0.0, noise(2, []), True)], "captured")
    (tmp_path / "transcript.json").write_text("{}")
    assert (main(["sounds", str(tmp_path)]), (tmp_path / "sounds.json").exists()) == (0, True)


def test_sounds_decodes_a_recording_whose_pcm_is_gone_and_leaves_none_behind(tmp_path):
    tone(tmp_path / "raw.mp3", 4)
    write_status(tmp_path, {"state": "captured", "source": "x", "startedAt": 0.0, "endsAt": 0.0, "seconds": 4.0, "parts": [listing("raw.mp3", 0.0, 4, True)]})
    main(["sounds", str(tmp_path)])
    frames = json.loads((tmp_path / "sounds.json").read_text())["frames"]
    assert (len(frames), (tmp_path / "raw.pcm").exists()) == (7, False)
