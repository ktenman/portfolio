import json
import threading

import pytest
from pytest import approx
from support import Stub, noise, wait, write_recording

from radio_worker.recorder import read_status, write_status
from radio_worker.transcriber import pause_before, transcribe


def transcript(directory):
    return json.loads((directory / "transcript.json").read_text())


def spans(directory):
    return [value for chunk in transcript(directory)["chunks"] for value in (chunk["start"], chunk["end"])]


def starts(directory):
    return [word["start"] for word in transcript(directory)["words"]]


def test_pause_before_returns_the_middle_of_the_quietest_stretch():
    assert pause_before(noise(20, [11.0]) / 32768, 280.0) == approx(291.15, abs=0.01)


def test_transcriber_cuts_each_chunk_at_the_pause_before_its_mark(tmp_path):
    write_recording(tmp_path, [("raw.mp3", 0.0, noise(130, [52.0, 113.0]), True)], "captured")
    transcribe(tmp_path, Stub(), "stub", 60)
    assert spans(tmp_path) == approx([0, 52.15, 52.15, 113.15, 113.15, 130], abs=0.01)


def test_transcriber_looks_for_the_pause_only_before_the_mark(tmp_path):
    samples = noise(70, [50.0])
    samples[61 * 16000 : 65 * 16000] = 0
    write_recording(tmp_path, [("raw.mp3", 0.0, samples, True)], "captured")
    transcribe(tmp_path, Stub(), "stub", 60)
    assert spans(tmp_path)[1] == approx(50.15, abs=0.01)


def test_transcriber_times_words_from_the_start_of_the_recording(tmp_path):
    write_recording(tmp_path, [("raw.mp3", 0.0, noise(10, []), True), ("raw-part-002.mp3", 15.0, noise(10, []), True)], "partial")
    transcribe(tmp_path, Stub(), "stub", 60)
    assert starts(tmp_path) == [0.0, 15.0]


def test_transcriber_skips_audio_a_part_repeats_from_the_one_before(tmp_path):
    write_recording(tmp_path, [("raw.mp3", 0.0, noise(10, []), True), ("raw-part-002.mp3", 4.0, noise(10, []), True)], "partial")
    stub = Stub()
    transcribe(tmp_path, stub, "stub", 60)
    assert (stub.calls, starts(tmp_path)) == ([10.0, 4.0], [0.0, 10.0])


def test_transcriber_resumes_after_the_last_finished_chunk(tmp_path):
    write_recording(tmp_path, [("raw.mp3", 0.0, noise(130, [52.0, 113.0]), True)], "captured")
    with pytest.raises(RuntimeError):
        transcribe(tmp_path, Stub(fail_on=2), "stub", 60)
    fresh = Stub()
    transcribe(tmp_path, fresh, "stub", 60)
    assert fresh.calls == approx([61.0, 16.85], abs=0.01)


def test_transcriber_drops_a_truncated_last_line(tmp_path):
    write_recording(tmp_path, [("raw.mp3", 0.0, noise(130, [52.0, 113.0]), True)], "captured")
    with pytest.raises(RuntimeError):
        transcribe(tmp_path, Stub(fail_on=2), "stub", 60)
    with (tmp_path / "transcript.jsonl").open("a") as file:
        file.write('{"part": "raw.mp3", "fr')
    transcribe(tmp_path, Stub(), "stub", 60)
    assert [json.loads(line)["to"] for line in (tmp_path / "transcript.jsonl").read_text().splitlines()] == approx([52.15, 113.15, 130], abs=0.01)


def test_transcriber_skips_whisper_for_a_part_without_audio(tmp_path):
    write_recording(tmp_path, [("raw.mp3", 0.0, noise(0, []), True)], "partial")
    stub = Stub()
    transcribe(tmp_path, stub, "stub", 60)
    assert (stub.calls, starts(tmp_path)) == ([], [])


def test_transcriber_transcribes_while_the_recording_is_still_running(tmp_path):
    write_recording(tmp_path, [("raw.mp3", 0.0, noise(70, [50.0]), False)], "recording")
    running = threading.Thread(target=transcribe, args=(tmp_path, Stub(), "stub", 60))
    running.start()
    wait(lambda: "\n" in ((tmp_path / "transcript.jsonl").read_text() if (tmp_path / "transcript.jsonl").exists() else ""), 10)
    first = json.loads((tmp_path / "transcript.jsonl").read_text().splitlines()[0])
    status = read_status(tmp_path)
    status["parts"][0]["durationSeconds"] = 70.0
    write_status(tmp_path, status | {"state": "captured"})
    running.join(10)
    assert (first["to"], running.is_alive(), spans(tmp_path)[-1]) == (approx(50.15, abs=0.01), False, 70.0)


def test_transcriber_writes_srt_cues(tmp_path):
    write_recording(tmp_path, [("raw.mp3", 0.0, noise(2.5, []), True)], "captured")
    transcribe(tmp_path, Stub(), "stub", 60)
    assert (tmp_path / "transcript.srt").read_text() == "1\n00:00:00,000 --> 00:00:02,500\nlõik 1\n\n"


def test_transcriber_writes_one_line_per_segment(tmp_path):
    write_recording(tmp_path, [("raw.mp3", 0.0, noise(2, []), True), ("raw-part-002.mp3", 2.0, noise(2, []), True)], "partial")
    transcribe(tmp_path, Stub(), "stub", 60)
    assert (tmp_path / "transcript.txt").read_text() == "lõik 1\nlõik 2\n"


def test_transcriber_leaves_a_finished_transcript_alone(tmp_path):
    write_recording(tmp_path, [("raw.mp3", 0.0, noise(2, []), True)], "captured")
    (tmp_path / "transcript.json").write_text("{}")
    stub = Stub()
    transcribe(tmp_path, stub, "stub", 60)
    assert stub.calls == []
