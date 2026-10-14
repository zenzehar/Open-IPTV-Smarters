#!/usr/bin/env python3
"""Synthetic Xtream/M3U/EPG/seekable MP4 server. CI-only; no private credentials."""
import argparse, json, os, re, shutil, subprocess, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, parse_qs
from pathlib import Path

USER, PASSWORD = "fixture", "testonly"
class Server(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, addr, video=None):
        super().__init__(addr, Handler)
        self.video, self.calls, self.lock = video, {}, threading.Lock()

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *args): pass # Never print credential-bearing URLs.
    def output(self, data, status=200, mime="application/json", headers=None):
        if not isinstance(data, bytes): data = json.dumps(data).encode()
        self.send_response(status)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(data)))
        for k, v in (headers or {}).items(): self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD": self.wfile.write(data)
    def do_HEAD(self): self.do_GET()
    def do_GET(self):
        parsed = urlsplit(self.path); p = parsed.path; q = parse_qs(parsed.query)
        action = q.get("action", ["login"])[0]
        key = action if p == "/player_api.php" else p.split("/")[1:2][0] if p != "/" else "/"
        with self.server.lock: self.server.calls[key] = self.server.calls.get(key, 0) + 1
        if p == "/health": return self.output({"ok": True, "media": bool(self.server.video)})
        if p == "/__stats": return self.output(dict(self.server.calls))
        if p == "/__error": return self.output({"error": "synthetic"}, int(q.get("status", ["503"])[0]))
        if p == "/player_api.php":
            if q.get("username", [""])[0] != USER or q.get("password", [""])[0] != PASSWORD:
                return self.output({"user_info": {"auth": 0, "status": "Disabled"}})
            if action == "login":
                return self.output({"user_info": {"auth": 1, "status": "Active", "username": USER,
                    "exp_date": "1893456000", "max_connections": "4", "active_cons": "0"},
                    "server_info": {"timezone": "Australia/Adelaide"}})
            if action.endswith("_categories"):
                kind = {"get_live_categories": "live", "get_vod_categories": "movie",
                        "get_series_categories": "series"}.get(action, "live")
                return self.output([{"category_id": kind+str(i), "category_name": "Fixture "+kind.title()+str(i)}
                                    for i in range(1, 3)])
            if action == "get_live_streams":
                return self.output([{"name": f"Fixture Channel {i:02}", "stream_id": str(i),
                    "category_id": "live1", "epg_channel_id": "fixture.channel1",
                    "stream_icon": "", "num": i} for i in range(1, 13)])
            if action == "get_vod_streams":
                return self.output([{"name": f"Fixture Movie {i:04}", "stream_id": str(i),
                    "category_id": "movie1" if i % 2 else "movie2", "container_extension": "mp4",
                    "added": str(1700000000+i), "year": "2025"} for i in range(1, 2306)])
            if action == "get_series":
                return self.output([{"name": f"Fixture Series {i:02}", "series_id": str(i),
                    "category_id": "series1", "cover": "", "last_modified": "1700000000"}
                    for i in range(1, 10)])
            if action == "get_series_info":
                return self.output({"episodes": {"1": [{"id": "201", "episode_num": 1,
                    "season": 1, "title": "Fixture S01E01", "container_extension": "mp4",
                    "info": {"duration_secs": 18}}],
                    "2": [{"id": "202", "episode_num": 1, "season": 2,
                    "title": "Fixture S02E01", "container_extension": "mp4",
                    "info": {"duration_secs": 18}}]}})
            if action == "get_vod_info":
                return self.output({"info": {"duration_secs": 18,
                    "plot": "Synthetic test content"}, "movie_data": {}})
            return self.output([])
        if p == "/get.php":
            return self.output(b'#EXTM3U\n#EXTINF:-1 tvg-id="fixture.channel1" group-title="Fixture",Fixture Channel 01\nhttp://10.0.2.2:18080/fixture.mp4\n',
                               mime="audio/x-mpegurl")
        if p == "/xmltv.php":
            xml = ('<?xml version="1.0"?><tv><channel id="fixture.channel1">'
                   '<display-name>Fixture Channel 01</display-name></channel>'
                   '<programme start="20261014000000 +0000" stop="20261014235959 +0000" '
                   'channel="fixture.channel1"><title>Fixture Programme</title></programme></tv>')
            return self.output(xml.encode(), mime="application/xml")
        if p.endswith(".m3u8"):
            return self.output(b'#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:18\n'
                               b'#EXTINF:18,\nhttp://10.0.2.2:18080/fixture.mp4\n#EXT-X-ENDLIST\n',
                               mime="application/vnd.apple.mpegurl")
        if p.endswith((".mp4", ".ts")):
            if not self.server.video or not self.server.video.is_file():
                return self.output(b"No synthetic media generated", 503, "text/plain")
            size = self.server.video.stat().st_size
            rng = self.headers.get("Range")
            begin, end = 0, size-1
            if rng:
                match = re.fullmatch(r"bytes=(\d+)-(\d*)", rng)
                if not match: return self.output(b"", 416, headers={"Content-Range": f"bytes */{size}"})
                begin = int(match[1]); end = min(size-1, int(match[2])) if match[2] else size-1
                if begin >= size or begin > end:
                    return self.output(b"", 416, headers={"Content-Range": f"bytes */{size}"})
            self.send_response(206 if rng else 200)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(end-begin+1))
            if rng: self.send_header("Content-Range", f"bytes {begin}-{end}/{size}")
            self.end_headers()
            if self.command == "HEAD": return
            with self.server.video.open("rb") as f:
                f.seek(begin); left = end-begin+1
                while left > 0:
                    data = f.read(min(65536, left))
                    if not data: break
                    self.wfile.write(data); left -= len(data)
            return
        return self.output(b"Not found", 404, "text/plain")

def make_video(path):
    if not shutil.which("ffmpeg"): return False
    try:
        subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "testsrc2=size=320x180:rate=12",
            "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=22050", "-t", "18",
            "-c:v", "mpeg4", "-q:v", "8", "-c:a", "aac",
            "-movflags", "+faststart", str(path)],
            stdout=subprocess.DEVNULL, timeout=90, check=True)
        return path.stat().st_size > 0
    except Exception: return False

if __name__ == "__main__":
    cli = argparse.ArgumentParser()
    cli.add_argument("--port", type=int, default=18080)
    cli.add_argument("--media-file", default="/tmp/koky-fixture.mp4")
    cli.add_argument("--skip-media", action="store_true")
    args = cli.parse_args()
    media = Path(args.media_file)
    if not args.skip_media and not media.exists() and not make_video(media): media = None
    print("Mock Xtream ready; synthetic media="+str(bool(media)), flush=True)
    Server(("127.0.0.1", args.port), media).serve_forever()
