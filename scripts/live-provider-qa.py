#!/usr/bin/env python3
"""Read-only real Xtream provider smoke, private env secrets only.
Never log the provider URL, account ID, passwords, titles, stream URLs, or bodies.
HTTPS is mandatory unless the operator explicitly enables unencrypted HTTP.
"""
import json
import os
import ssl
import sys
import urllib.parse as urlparse
import urllib.error
import urllib.request
from pathlib import Path

MAX_CATALOGUE_BYTES = 60 * 1024 * 1024
REPORT_FILE = Path("live-provider-qa-summary.json")

class PrivateCheckError(Exception):
    """Keep exception text independent of provider URL/credentials."""

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

class Client:
    def __init__(self):
        raw = os.environ.get("KOKY_PROVIDER_URL", "").strip()
        self.user = os.environ.get("KOKY_PROVIDER_USERNAME", "")
        self.password = os.environ.get("KOKY_PROVIDER_PASSWORD", "")
        self.insecure = os.environ.get("KOKY_ALLOW_HTTP", "false").lower() == "true"
        if not raw or not self.user or not self.password:
            raise PrivateCheckError("Missing protected GitHub environment secrets")
        try:
            u = urlparse.urlsplit(raw)
            if u.scheme not in ("http", "https") or not u.hostname or u.username or u.password:
                raise ValueError()
            if u.scheme == "http" and not self.insecure:
                # Upgrade before sending ANY credentials. Do not reuse port 80.
                scheme = "https"
                port = None if u.port in (None, 80) else u.port
            else:
                scheme, port = u.scheme, u.port
            host = u.hostname
            if ":" in host: host = "[" + host + "]"
            netloc = host + ((":" + str(port)) if port else "")
            self.base = urlparse.urlunsplit((scheme, netloc, "", "", ""))
            self.scheme = scheme
        except (ValueError, TypeError) as exc:
            raise PrivateCheckError("Invalid protected base URL") from None
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}),
            urllib.request.HTTPSHandler(context=ssl.create_default_context()),
            NoRedirect(),
        )

    def request(self, path, query=None, *, max_bytes=MAX_CATALOGUE_BYTES,
                headers=None):
        # No redirect following (including cross-origin redirects carrying credentials).
        uri = self.base + path
        if query: uri += "?" + urlparse.urlencode(query)
        req = urllib.request.Request(uri, headers={
            "User-Agent": "OpenTV/0.1 (Android)",
            "Accept": "application/json, */*",
            **(headers or {}),
        })
        try:
            with self.opener.open(req, timeout=20) as res:
                status = res.status
                body = res.read(max_bytes + 1)
                if len(body) > max_bytes:
                    raise PrivateCheckError("Response exceeded private QA safety limit")
                return status, dict(res.headers), body
        except urllib.error.HTTPError as e:
            # NEVER interpolate/print the HTTPError (it contains a credential URL).
            raise PrivateCheckError("Server rejected or redirected a request") from None
        except (OSError, ValueError, TimeoutError, ssl.SSLError):
            raise PrivateCheckError("Connection failed or TLS was unavailable") from None

    def api(self, action=None, **extra):
        query = {"username": self.user, "password": self.password}
        if action: query["action"] = action
        query.update(extra)
        _, _, raw = self.request("/player_api.php", query)
        try:
            return json.loads(raw)
        except (ValueError, UnicodeError):
            raise PrivateCheckError("Provider returned an invalid JSON catalogue") from None

def require(condition, reason):
    if not condition: raise PrivateCheckError(reason)

def rows(value):
    require(isinstance(value, list), "Provider returned an unexpected list shape")
    return [v for v in value if isinstance(v, dict)]

def test(client, probe_media=False):
    result = []
    def record(label, operation):
        try:
            operation()
            result.append({"check": label, "status": "PASS"})
            print("PASS " + label, flush=True)
        except PrivateCheckError:
            result.append({"check": label, "status": "FAIL"})
            print("FAIL " + label + " (private details suppressed)", flush=True)
        except Exception:
            result.append({"check": label, "status": "FAIL"})
            print("FAIL " + label + " (private details suppressed)", flush=True)

    def auth():
        v = client.api()
        require(isinstance(v, dict) and isinstance(v.get("user_info"), dict),
                "Missing account info")
        u = v["user_info"]
        require(str(u.get("auth")) in ("1", "True") and
                str(u.get("status", "Active")).lower() == "active",
                "Authentication rejected")
    record("authentication", auth)
    if result[-1]["status"] != "PASS": return result

    for action, label, key in [
        ("get_live_categories", "live_categories", "category_id"),
        ("get_vod_categories", "movie_categories", "category_id"),
        ("get_series_categories", "series_categories", "category_id"),
        ("get_live_streams", "live_catalogue", "stream_id"),
        ("get_vod_streams", "movie_catalogue", "stream_id"),
        ("get_series", "series_catalogue", "series_id"),
    ]:
        def validate(action=action, key=key):
            entries = rows(client.api(action))
            require(bool(entries), "Catalogue has no valid entries")
            require(any(e.get(key) is not None for e in entries),
                    "Catalogue missing identifiers")
        record(label, validate)

    def episode_shape():
        shows = rows(client.api("get_series"))
        require(bool(shows), "No series records available")
        first_id = next((s.get("series_id") for s in shows if s.get("series_id")), None)
        require(first_id is not None, "Series identifier missing")
        details = client.api("get_series_info", series_id=str(first_id))
        require(isinstance(details, dict) and isinstance(details.get("episodes"), (dict, list)),
                "Episodes have an unexpected schema")
    record("episode_metadata", episode_shape)

    if probe_media:
        def range_probe():
            movies = rows(client.api("get_vod_streams"))
            first = next((m for m in movies if m.get("stream_id")), None)
            require(first is not None, "No candidate VOD stream")
            ext = str(first.get("container_extension") or "mp4")
            require(ext.isalnum() and len(ext) < 12, "Invalid stream extension")
            p = "/movie/" + "/".join(urlparse.quote(str(v), safe="") for v in (
                client.user, client.password, first["stream_id"])) + "." + ext
            status, headers, body = client.request(
                p, max_bytes=8192, headers={"Range": "bytes=0-8191"})
            require(status in (200, 206), "Stream did not return a media response")
            require(bool(body), "Empty stream sample")
        record("bounded_media_range_probe", range_probe)
    else:
        result.append({"check": "bounded_media_range_probe", "status": "NOT_REQUESTED"})
    return result

def main():
    report = {"scope": "read_only_private_xtream", "checks": []}
    try:
        client = Client()
        if client.insecure and client.scheme == "http":
            print("WARNING: unencrypted HTTP explicitly enabled; credentials will travel in plaintext.")
        report["checks"] = test(
            client, os.environ.get("KOKY_PROBE_MEDIA", "false").lower() == "true"
        )
        failed = any(r["status"] == "FAIL" for r in report["checks"])
    except PrivateCheckError:
        print("FAIL setup_or_secure_transport (private details suppressed)")
        report["checks"] = [{"check": "setup_or_secure_transport", "status": "FAIL"}]
        failed = True
    except Exception:
        print("FAIL setup_or_secure_transport (private details suppressed)")
        report["checks"] = [{"check": "setup_or_secure_transport", "status": "FAIL"}]
        failed = True
    # This file NEVER includes URLs, usernames, passwords, catalogue names or bodies.
    REPORT_FILE.write_text(json.dumps(report, indent=2) + "\n")
    sys.exit(1 if failed else 0)

if __name__ == "__main__":
    main()
