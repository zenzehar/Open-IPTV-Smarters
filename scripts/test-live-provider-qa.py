#!/usr/bin/env python3
"""Security-contract checks for the private live-provider test runner.
All credentials here are synthetic, never a user's real values.
"""
import importlib.util
import io
import os
from pathlib import Path
import tempfile
import threading
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parent

def import_script(filename, modname):
    spec = importlib.util.spec_from_file_location(modname, SCRIPTS / filename)
    obj = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(obj)
    return obj

live = import_script("live-provider-qa.py", "koky_private_live")
fake = import_script("mock-xtream-server.py", "koky_local_fake")

class ConfidentialProviderChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = fake.Server(("127.0.0.1", 0), video=None)
        cls.port = cls.server.server_port
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def secrets(self, allow_http):
        return {
            "KOKY_PROVIDER_URL": "http://127.0.0.1:%d" % self.port,
            "KOKY_PROVIDER_USERNAME": "fixture",
            "KOKY_PROVIDER_PASSWORD": "testonly",
            "KOKY_ALLOW_HTTP": "true" if allow_http else "false",
            "KOKY_PROBE_MEDIA": "false",
        }

    def test_explicit_plaintext_opt_in_is_required_and_secret_values_are_hidden(self):
        with patch.dict(os.environ, self.secrets(True), clear=True):
            client = live.Client()
            output = io.StringIO()
            with redirect_stdout(output):
                results = live.test(client, False)
        rendered = output.getvalue()
        self.assertTrue(results)
        self.assertTrue(all(x["status"] == "PASS"
                            for x in results if x["check"] != "bounded_media_range_probe"))
        for secret in ("fixture", "testonly", "127.0.0.1"):
            self.assertNotIn(secret, rendered)

    def test_https_upgrade_is_fail_closed_when_endpoint_is_http_only(self):
        with patch.dict(os.environ, self.secrets(False), clear=True):
            client = live.Client()
            self.assertEqual(client.scheme, "https")
            with self.assertRaises(live.PrivateCheckError):
                client.api()

    def test_missing_secrets_are_rejected_without_disclosure(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(live.PrivateCheckError):
                live.Client()

if __name__ == "__main__":
    unittest.main(verbosity=2)
