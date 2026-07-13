"""Regression tests for parental PIN verification and playlist merge uploads (Nimi/Open Source)."""

import json
import os
from pathlib import Path

import pytest
import requests


APP_ROOT = Path("/app")
SERVER_FILE = APP_ROOT / "server.js"
CONFIG_FILE = APP_ROOT / "data" / "channel_config.json"
NIMI_FILE = APP_ROOT / "data" / "nimi_channels.m3u"
OPEN_SOURCE_FILE = APP_ROOT / "data" / "open_source_channels.m3u"

BASE_URL = (os.environ.get("K1_BASE_URL") or "http://127.0.0.1:3000/k1").rstrip("/")
ADMIN_USERNAME = os.environ.get("K1_ADMIN_USERNAME") or "admin"
ADMIN_PASSWORD = os.environ.get("K1_ADMIN_PASSWORD") or "kanaliim2024"


@pytest.fixture(scope="module", autouse=True)
def restore_data_files_after_tests():
    """Keep test side effects isolated by restoring changed data files at module end."""
    original_config = CONFIG_FILE.read_text(encoding="utf-8") if CONFIG_FILE.exists() else None
    original_nimi = NIMI_FILE.read_text(encoding="utf-8") if NIMI_FILE.exists() else "#EXTM3U\n"
    original_open_source = OPEN_SOURCE_FILE.read_text(encoding="utf-8") if OPEN_SOURCE_FILE.exists() else "#EXTM3U\n"
    yield
    if original_config is not None:
        CONFIG_FILE.write_text(original_config, encoding="utf-8")
    NIMI_FILE.write_text(original_nimi, encoding="utf-8")
    OPEN_SOURCE_FILE.write_text(original_open_source, encoding="utf-8")


@pytest.fixture(scope="module")
def admin_session():
    """Authenticated admin session for admin-only routes."""
    session = requests.Session()
    session.headers.update({"X-Forwarded-For": "203.0.113.10"})
    response = session.post(
        f"{BASE_URL}/login",
        data={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD},
        allow_redirects=False,
        timeout=30,
    )
    assert response.status_code in (302, 303)
    assert "/admin" in response.headers.get("location", "")
    return session


def _feature_payload_with_pin(pin_code: str, locked: str = "builtin:nimi"):
    cfg = json.loads(CONFIG_FILE.read_text(encoding="utf-8")) if CONFIG_FILE.exists() else {}
    flags = cfg.get("feature_flags") or {}

    bool_fields = [
        "websocket_live_updates",
        "websocket_watch_party",
        "websocket_channel_chat",
        "websocket_reactions",
        "epg_grid_ui",
        "player_epg_overlay",
        "chromecast_airplay",
        "web_push_notifications",
        "channel_preview_hover",
        "deep_links_share",
        "bandwidth_dashboard",
        "android_tv_ui",
        "pwa_enabled",
    ]

    payload = {
        "f_parental_controls_pin": "1",
        "f_parental_locked_categories": locked,
        "f_parental_pin_code": pin_code,
    }
    for field in bool_fields:
        payload[f"f_{field}"] = "1" if flags.get(field) is True else "0"
    return payload


def _set_parental_pin(admin_session, pin_code: str, locked: str = "builtin:nimi"):
    response = admin_session.post(
        f"{BASE_URL}/admin/features",
        data=_feature_payload_with_pin(pin_code=pin_code, locked=locked),
        allow_redirects=False,
        timeout=30,
    )
    assert response.status_code in (302, 303)
    assert "/admin/features?success=" in response.headers.get("location", "")


# Auth + parental PIN API behavior
def test_login_sets_http_only_cookie():
    session = requests.Session()
    response = session.post(
        f"{BASE_URL}/login",
        data={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD},
        headers={"X-Forwarded-For": "203.0.113.11"},
        allow_redirects=False,
        timeout=30,
    )
    assert response.status_code in (302, 303)

    set_cookie = response.headers.get("set-cookie", "")
    assert "HttpOnly" in set_cookie


def test_parental_pin_accepts_valid_0808_and_rejects_invalid(admin_session):
    _set_parental_pin(admin_session, pin_code="0808")

    valid = admin_session.post(
        f"{BASE_URL}/api/parental/verify", json={"pin": "0808"}, timeout=20
    )
    assert valid.status_code == 200
    valid_json = valid.json()
    assert valid_json.get("enabled") is True
    assert valid_json.get("valid") is True

    invalid = admin_session.post(
        f"{BASE_URL}/api/parental/verify", json={"pin": "0000"}, timeout=20
    )
    assert invalid.status_code == 200
    invalid_json = invalid.json()
    assert invalid_json.get("enabled") is True
    assert invalid_json.get("valid") is False

    bad_format = admin_session.post(
        f"{BASE_URL}/api/parental/verify", json={"pin": "12ab"}, timeout=20
    )
    assert bad_format.status_code == 200
    bad_json = bad_format.json()
    assert bad_json.get("reason") == "invalid_format"


def test_parental_verify_contains_legacy_compatibility_paths():
    source = SERVER_FILE.read_text(encoding="utf-8")
    assert "const legacyHash = hashTextFastLegacyServer(pin);" in source
    assert "const legacySha256Hash = crypto.createHash('sha256').update(pin).digest('hex').toLowerCase();" in source
    assert "const validRaw = /^\\d{4}$/.test(expected) && expected === pin;" in source
    assert "expected === secureHash || expected === legacyHash || expected === legacySha256Hash || validRaw" in source


# Nimi/Open Source upload merge behavior
def test_upload_nimi_playlist_merges_and_adds_backup_links(admin_session):
    base_m3u = """#EXTM3U
#EXTINF:-1,Nimi Prime
https://old.example.com/live/nimi-prime.m3u8
"""
    NIMI_FILE.write_text(base_m3u, encoding="utf-8")

    incoming_m3u = """#EXTM3U
#EXTINF:-1,Nimi Prime!!
https://new.example.com/live/nimi-prime.m3u8
#EXTINF:-1,Nimi Extra
https://new.example.com/live/nimi-extra.m3u8
"""
    files = {"playlist": ("nimi-upload.m3u", incoming_m3u.encode("utf-8"), "audio/x-mpegurl")}
    response = admin_session.post(
        f"{BASE_URL}/admin/upload",
        files=files,
        allow_redirects=False,
        timeout=60,
    )

    assert response.status_code in (302, 303)
    location = response.headers.get("location", "")
    assert "%2B1%20kanale%20t%C3%AB%20reja" in location
    assert "%2B1%20backup%20links" in location

    merged = NIMI_FILE.read_text(encoding="utf-8")
    assert "https://old.example.com/live/nimi-prime.m3u8" in merged
    assert "https://new.example.com/live/nimi-prime.m3u8" in merged
    assert "https://new.example.com/live/nimi-extra.m3u8" in merged


def test_upload_open_source_playlist_merges_and_adds_backup_links(admin_session):
    base_m3u = """#EXTM3U
#EXTINF:-1,Open Source Alpha
https://old.example.com/live/os-alpha.m3u8
"""
    OPEN_SOURCE_FILE.write_text(base_m3u, encoding="utf-8")

    incoming_m3u = """#EXTM3U
#EXTINF:-1,open source alpha
https://new.example.com/live/os-alpha.m3u8
#EXTINF:-1,Open Source Beta
https://new.example.com/live/os-beta.m3u8
"""
    files = {"playlist": ("open-source-upload.m3u", incoming_m3u.encode("utf-8"), "audio/x-mpegurl")}
    response = admin_session.post(
        f"{BASE_URL}/admin/upload-open-source",
        files=files,
        allow_redirects=False,
        timeout=60,
    )

    assert response.status_code in (302, 303)
    location = response.headers.get("location", "")
    assert "%2B1%20kanale%20t%C3%AB%20reja" in location
    assert "%2B1%20backup%20links" in location

    merged = OPEN_SOURCE_FILE.read_text(encoding="utf-8")
    assert "https://old.example.com/live/os-alpha.m3u8" in merged
    assert "https://new.example.com/live/os-alpha.m3u8" in merged
    assert "https://new.example.com/live/os-beta.m3u8" in merged


# Auth playbook check: brute-force lockout threshold should be 5 (plus one blocked attempt)
def test_login_lockout_threshold_matches_playbook_requirement():
    source = SERVER_FILE.read_text(encoding="utf-8")
    assert "rateLimitAllow(loginRateLimits, ip, 5," in source
