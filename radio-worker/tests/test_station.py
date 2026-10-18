import threading
import urllib.request

from pytest import approx
from support import broadcast, replay, serving

from radio_worker.station import Station


def test_station_serves_the_recording_as_live_hls(tmp_path):
    with broadcast(tmp_path) as url, urllib.request.urlopen(url, timeout=30) as response:
        playlist = response.read().decode()
        kind = response.headers["Content-Type"]
    assert (kind, "#EXT-X-TARGETDURATION:6" in playlist, "seg_00000.ts" in playlist) == ("application/vnd.apple.mpegurl", True, True)


def test_station_serves_segments_as_mpeg_ts(tmp_path):
    with broadcast(tmp_path, seconds=2) as url:
        urllib.request.urlopen(url, timeout=30).close()
        with urllib.request.urlopen(url.replace("stream.m3u8", "seg_00000.ts"), timeout=30) as response:
            kind = response.headers["Content-Type"]
    assert kind == "video/mp2t"


def test_station_starts_part_way_with_from(tmp_path):
    server = replay(tmp_path, start=6)
    with serving(server) as url:
        urllib.request.urlopen(url, timeout=30).close()
        server.process.wait(30)
        playlist = (tmp_path / "hls" / "stream.m3u8").read_text()
    assert sum(float(line[8:].rstrip(",")) for line in playlist.splitlines() if line.startswith("#EXTINF:")) == approx(7.0, abs=0.1)


def test_station_goes_off_air_after_the_replay_ends(tmp_path, monkeypatch):
    monkeypatch.setattr("radio_worker.station.GRACE_SECONDS", 0.1)
    server = replay(tmp_path, seconds=2)
    listening = threading.Thread(target=server.serve_forever, daemon=True)
    listening.start()
    urllib.request.urlopen(f"http://127.0.0.1:{server.server_port}/stream.m3u8", timeout=30).close()
    listening.join(40)
    server.server_close()
    assert not listening.is_alive()


def test_station_listens_on_the_given_host(tmp_path):
    server = Station(tmp_path / "in.mp3", tmp_path, port=0, start=0.0, speed=1.0, host="0.0.0.0")
    server.server_close()
    assert server.server_address[0] == "0.0.0.0"
