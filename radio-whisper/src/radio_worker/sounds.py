from collections.abc import Callable
from pathlib import Path

import numpy as np
from ai_edge_litert.interpreter import Interpreter

from radio_worker.recorder import RATE

MODEL = Path(__file__).with_name("yamnet.tflite")
WINDOW = 15600
STRIDE = RATE // 2
SPEECH = 0
MUSIC = 132


def frames(parts: list[tuple[Path, float, float]]) -> list[dict]:
    classify = load()
    return [frame for path, origin, skip in parts for frame in hear(classify, path, origin, skip)]


def hear(classify: Callable[[np.ndarray], np.ndarray], path: Path, origin: float, skip: float) -> list[dict]:
    count = path.stat().st_size // 2 if path.exists() else 0
    return [
        scored(classify(np.fromfile(path, "<i2", WINDOW, offset=first * 2).astype(np.float32) / 32768), origin + (first + WINDOW / 2) / RATE)
        for first in range(round(skip * RATE), count - WINDOW + 1, STRIDE)
    ]


def scored(scores: np.ndarray, at: float) -> dict:
    return {"at": round(at, 3), "speech": round(float(scores[SPEECH]), 3), "music": round(float(scores[MUSIC]), 3)}


def load() -> Callable[[np.ndarray], np.ndarray]:
    interpreter = Interpreter(model_path=str(MODEL), num_threads=1)
    interpreter.allocate_tensors()
    source = interpreter.get_input_details()[0]["index"]
    target = interpreter.get_output_details()[0]["index"]

    def classify(window: np.ndarray) -> np.ndarray:
        interpreter.set_tensor(source, window)
        interpreter.invoke()
        return interpreter.get_tensor(target).reshape(-1)

    return classify
