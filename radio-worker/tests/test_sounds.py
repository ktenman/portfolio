from pathlib import Path

import numpy as np
from pytest import approx
from support import melody

from radio_worker.recorder import RATE
from radio_worker.sounds import frames


def heard(directory: Path, samples: np.ndarray, origin: float = 0.0, skip: float = 0.0) -> list[dict]:
    path = directory / "raw.pcm"
    samples.astype("<i2").tofile(path)
    return frames([(path, origin, skip)])


def test_a_plucked_melody_is_heard_as_music(tmp_path):
    found = heard(tmp_path, melody(8))
    assert sum(frame["music"] >= 0.5 and frame["speech"] < 0.3 for frame in found) > len(found) / 2


def test_silence_is_heard_as_neither_speech_nor_music(tmp_path):
    found = heard(tmp_path, np.zeros(4 * RATE, np.int16))
    assert max(max(frame["speech"], frame["music"]) for frame in found) < 0.3


def test_each_frame_sits_at_the_middle_of_its_window_in_recording_time(tmp_path):
    found = heard(tmp_path, np.zeros(2 * RATE, np.int16), origin=15.0)
    assert [frame["at"] for frame in found] == approx([15.4875, 15.9875, 16.4875], abs=0.001)


def test_hearing_starts_after_the_audio_it_skips(tmp_path):
    found = heard(tmp_path, np.zeros(10 * RATE, np.int16), origin=4.0, skip=6.0)
    assert (len(found), found[0]["at"]) == (7, approx(10.4875, abs=0.001))
