#!/usr/bin/env python3
"""Host-side mock provider contracts. No Android SDK or real credentials needed."""
import importlib.util
import json
from pathlib import Path
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

script = Path(__file__).with_name("mock-xtream-server.py")
spec = importlib.util.spec_from_file_location("mock_xtream", script)
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)

class MockProviderContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.video = Path(cls.tmp.name) / "test.mp4"
        cls.video.write_bytes(bytes(range(256)) * 64)
        cls.server = fixture.Server(("127.0.0.1", 0), cls.video)
        cls.port = cls.server.server_port
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.tmp.cleanup()
    def get(self, path, headers=None):
        with urlopen(Request("http://127.0.0.1:%d%s" % (self.port, path),
                             headers=headers or {}), timeout=10) as r:
            return r.status, dict(r.headers), r.read()
    def api(self, action=""):
        return "/player_api.php?username=fixture&password=testonly" + ("&action="+action if action else "")
    def test_health(self):
        self.assertEqual(json.loads(self.get("/health")[2])["ok"], True)
    def test_valid_and_invalid_login(self):
        self.assertEqual(json.loads(self.get(self.api())[2])["user_info"]["auth"], 1)
        self.assertEqual(json.loads(self.get("/player_api.php?username=wrong&password=testonly")[2])["user_info"]["auth"], 0)
    def test_large_movies_and_categories(self):
        self.assertEqual(len(json.loads(self.get(self.api("get_vod_streams"))[2])), 2305)
        for action in ("get_vod_categories", "get_live_categories", "get_series_categories"):
            self.assertEqual(len(json.loads(self.get(self.api(action))[2])), 2)
    def test_episode_order(self):
        obj = json.loads(self.get(self.api("get_series_info")+"&series_id=1")[2])
        self.assertEqual(set(obj["episodes"].keys()), {"1", "2"})
        self.assertEqual(obj["episodes"]["2"][0]["episode_num"], 1)
    def test_xmltv_and_m3u(self):
        self.assertIn(b"Fixture Channel", self.get("/get.php")[2])
        self.assertIn(b"Fixture Programme", self.get("/xmltv.php")[2])
    def test_range_206_seekable_and_416(self):
        code, headers, data = self.get("/movie/fixture/testonly/1.mp4",
                                      {"Range": "bytes=400-499"})
        self.assertEqual((code, len(data)), (206, 100))
        self.assertEqual(headers["Content-Range"], "bytes 400-499/16384")
        self.assertEqual(data, (bytes(range(256))*64)[400:500])
        with self.assertRaises(HTTPError) as err:
            self.get("/movie/fixture/testonly/1.mp4", {"Range": "bytes=18000-"})
        self.assertEqual(err.exception.code, 416)
    def test_error_responses(self):
        with self.assertRaises(HTTPError) as err:
            self.get("/__error?status=503")
        self.assertEqual(err.exception.code, 503)

if __name__ == "__main__":
    unittest.main(verbosity=2)
