import functools
import subprocess
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

GRACE_SECONDS = 12


class Handler(SimpleHTTPRequestHandler):
    extensions_map = {".m3u8": "application/vnd.apple.mpegurl", ".ts": "video/mp2t"}

    def do_GET(self) -> None:
        if self.path.split("?")[0] == "/stream.m3u8":
            self.server.on_air()
        super().do_GET()

    def log_message(self, format: str, *args: object) -> None:
        pass


class Station(ThreadingHTTPServer):
    process: subprocess.Popen | None = None

    def __init__(self, recording: Path, directory: Path, port: int, start: float, speed: float, host: str) -> None:
        super().__init__((host, port), functools.partial(Handler, directory=str(directory)))
        self.recording = recording
        self.directory = directory
        self.start = start
        self.speed = speed
        self.lock = threading.Lock()
        self.replay: threading.Thread | None = None

    def on_air(self) -> None:
        with self.lock:
            if self.replay is None:
                self.replay = threading.Thread(target=self.play, daemon=True)
                self.replay.start()
        while not (self.directory / "stream.m3u8").exists() and self.replay.is_alive():
            time.sleep(0.1)

    def play(self) -> None:
        self.process = subprocess.Popen(self.command())
        self.process.wait()
        time.sleep(GRACE_SECONDS)
        self.shutdown()

    def command(self) -> list[str]:
        return [
            "ffmpeg", "-hide_banner", "-nostdin", "-loglevel", "error",
            "-readrate", str(self.speed), "-ss", str(self.start), "-i", str(self.recording),
            "-map", "0:a:0", "-c:a", "copy",
            "-f", "hls", "-hls_time", "6", "-hls_list_size", "0", "-hls_flags", "omit_endlist",
            "-hls_segment_filename", str(self.directory / "seg_%05d.ts"), str(self.directory / "stream.m3u8"),
        ]

    def server_close(self) -> None:
        super().server_close()
        if self.process is not None:
            self.process.terminate()
            self.process.wait()
