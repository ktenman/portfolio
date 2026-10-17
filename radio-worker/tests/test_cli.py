import hashlib
import json

import pytest
from support import Stub, broadcast

from radio_worker.cli import main, run
from radio_worker.recorder import read_status


def test_run_records_and_transcribes_a_replay(tmp_path):
    with broadcast(tmp_path) as url:
        state = run(tmp_path / "rec", url, 12, Stub, "stub", 60)
    assert (state, len(json.loads((tmp_path / "rec" / "transcript.json").read_text())["words"])) == ("captured", 1)


def test_run_only_finishes_the_transcript_of_an_ended_recording(tmp_path):
    directory = tmp_path / "rec"
    with broadcast(tmp_path) as url:
        run(directory, url, 12, Stub, "stub", 60)
    before = hashlib.sha256((directory / "raw.mp3").read_bytes()).hexdigest()
    (directory / "transcript.json").unlink()
    (directory / "transcript.jsonl").unlink()
    run(directory, url, 12, Stub, "stub", 60)
    after = hashlib.sha256((directory / "raw.mp3").read_bytes()).hexdigest()
    assert ([part["file"] for part in read_status(directory)["parts"]], after, (directory / "transcript.json").exists()) == (["raw.mp3"], before, True)


def test_run_command_refuses_a_directory_that_is_not_a_recording(tmp_path, capsys):
    (tmp_path / "model").mkdir()
    (tmp_path / "model" / "model.bin").write_bytes(b"")
    (tmp_path / "status.json").write_text('{"state": "captured_needs_review"}')
    with pytest.raises(SystemExit):
        main(["run", "--duration", "4200", "--out", str(tmp_path), "--model", str(tmp_path / "model")])
    assert "holds no recording made by this worker" in capsys.readouterr().err
