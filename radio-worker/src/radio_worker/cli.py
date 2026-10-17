import argparse
import contextlib
import tempfile
from pathlib import Path

from radio_worker.station import station


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="radio-worker")
    commands = parser.add_subparsers(dest="command", required=True)
    replay = commands.add_parser("station", help="replay a recording as a live HLS stream")
    replay.add_argument("recording", type=Path)
    replay.add_argument("--port", type=int, default=8770)
    replay.add_argument("--from", dest="start", type=float, default=0.0, metavar="SECONDS")
    replay.add_argument("--speed", type=float, default=1.0, metavar="FACTOR")
    args = parser.parse_args(argv)
    if not args.recording.is_file():
        parser.error(f"{args.recording} is not a file")
    return broadcast(args.recording, args.port, args.start, args.speed)


def broadcast(recording: Path, port: int, start: float, speed: float) -> int:
    with tempfile.TemporaryDirectory() as directory:
        server = station(recording, Path(directory), port, start, speed)
        print(f"On air at http://127.0.0.1:{server.server_port}/stream.m3u8", flush=True)
        with contextlib.suppress(KeyboardInterrupt):
            server.serve_forever()
        server.server_close()
    return 0
