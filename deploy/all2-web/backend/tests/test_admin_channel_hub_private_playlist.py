"""Regression tests for admin all-channels hub, channel CRUD/purge, and private playlist guards."""

import json
import os
import re
import time
from pathlib import Path

import pytest
import requests


APP_ROOT = Path("/app")
DATA_DIR = APP_ROOT / "data"

BASE_URL = (os.environ.get("K1_BASE_URL") or "http://127.0.0.1:3000/k1").rstrip("/")
ADMIN_USERNAME = os.environ.get("K1_ADMIN_USERNAME") or "admin"
ADMIN_PASSWORD = os.environ.get("K1_ADMIN_PASSWORD") or "kanaliim2024"
QA_TEST_IP = f"203.0.113.{50 + (int(time.time()) % 150)}"

CUSTOM_CH_FILE = DATA_DIR / "custom_channels.json"
OPEN_SOURCE_FILE = DATA_DIR / "open_source_channels.m3u"
NIMI_FILE = DATA_DIR / "nimi_channels.m3u"
CHANNEL_CONFIG_FILE = DATA_DIR / "channel_config.json"
CUSTOM_PLAYLISTS_FILE = DATA_DIR / "custom_playlists.json"
CUSTOM_CATEGORIES_FILE = DATA_DIR / "custom_categories.json"
PLAYLIST_ANALYTICS_FILE = DATA_DIR / "playlist_analytics.json"
CHANNEL_VIEWS_FILE = DATA_DIR / "channel_views.json"
AUDIT_LOG_FILE = DATA_DIR / "audit_log.json"
SOURCE_DELETE_SNAPSHOT_FILE = DATA_DIR / "source_delete_snapshot.json"


def _slugify(value: str) -> str:
    text = re.sub(r"[^\w\s-]", "", str(value or "").lower())
    return re.sub(r"[-\s]+", "-", text).strip("-")


def _parse_chmap_from_html(html: str) -> dict:
    match = re.search(r"const CHMAP=(\{.*?\});", html, flags=re.S)
    assert match, "CHMAP not found in all-channels page"
    return json.loads(match.group(1))


def _parse_m3u_urls_by_normalized_name(content: str) -> dict:
    out = {}
    current_name = ""
    for raw in str(content or "").splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#EXTINF"):
            parts = line.split(",", 1)
            current_name = parts[1].strip() if len(parts) > 1 else ""
            continue
        if line.startswith("#"):
            continue
        if line.startswith("http") and current_name:
            key = " ".join(re.sub(r"[^\w\s]", " ", current_name.lower()).split())
            out.setdefault(key, []).append(line)
    return out


def _find_slug(chmap: dict, source_key: str, channel_name: str) -> str:
    expected = str(channel_name or "").strip().lower()
    for slug, row in chmap.items():
        if str(row.get("sourceKey") or "") != source_key:
            continue
        if str(row.get("name") or "").strip().lower() == expected:
            return slug
    return ""


def _ensure_custom_channel_exists(session: requests.Session, channel_name: str = "Delete Me QA"):
    response = session.post(
        f"{BASE_URL}/api/custom-channel/add",
        json={
            "name": channel_name,
            "url": "https://example.com/live/delete-me-qa.m3u8",
            "category": "tv",
            "backup_urls": [],
        },
        timeout=30,
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload.get("ok") in (True, False)
    _refresh(session)


def _read_json(path: Path, default):
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


@pytest.fixture(scope="module", autouse=True)
def restore_data_files_after_tests():
    """Restore file-level side effects created by regression tests."""
    tracked = [
        CUSTOM_CH_FILE,
        OPEN_SOURCE_FILE,
        NIMI_FILE,
        CHANNEL_CONFIG_FILE,
        CUSTOM_PLAYLISTS_FILE,
        CUSTOM_CATEGORIES_FILE,
        PLAYLIST_ANALYTICS_FILE,
        CHANNEL_VIEWS_FILE,
        AUDIT_LOG_FILE,
        SOURCE_DELETE_SNAPSHOT_FILE,
    ]
    snapshots = {}
    for path in tracked:
        snapshots[path] = path.read_text(encoding="utf-8") if path.exists() else None
    yield
    for path, original in snapshots.items():
        if original is None:
            if path.exists():
                path.unlink()
        else:
            path.write_text(original, encoding="utf-8")

    # Rebuild in-memory DB from restored files.
    session = requests.Session()
    session.headers.update({"X-Forwarded-For": QA_TEST_IP})
    login = session.post(
        f"{BASE_URL}/login",
        data={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD},
        allow_redirects=False,
        timeout=30,
    )
    if login.status_code in (302, 303):
        session.get(f"{BASE_URL}/refresh", timeout=60)


@pytest.fixture(scope="module")
def admin_session():
    """Admin-authenticated session for admin pages and APIs."""
    session = requests.Session()
    session.headers.update({"X-Forwarded-For": QA_TEST_IP})
    response = session.post(
        f"{BASE_URL}/login",
        data={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD},
        allow_redirects=False,
        timeout=30,
    )
    assert response.status_code in (302, 303)
    assert "/admin" in response.headers.get("location", "")
    return session


def _refresh(session: requests.Session):
    response = session.get(f"{BASE_URL}/refresh", allow_redirects=False, timeout=60)
    assert response.status_code in (302, 303)


# Admin ALL Channels hub UI + actions wiring
def test_all_channels_hub_contains_edit_delete_actions(admin_session):
    response = admin_session.get(f"{BASE_URL}/admin/categories/all-channels", timeout=30)
    assert response.status_code == 200
    html = response.text
    assert "ALL Channels Hub" in html
    assert "editAdminChannel(" in html
    assert "deleteAdminChannel(" in html
    assert "api/admin/channel/update" in html
    assert "api/admin/channel/delete" in html


# Upload merge behavior (normalized duplicates + stats + primary becomes newest)
def test_nimi_upload_merges_duplicate_and_sets_new_primary(admin_session):
    old_url = "https://old.example.com/live/qa-nimi-prime.m3u8"
    new_url = "https://new.example.com/live/qa-nimi-prime.m3u8"
    extra_url = "https://new.example.com/live/qa-nimi-extra.m3u8"

    base = """#EXTM3U
#EXTINF:-1,QA Nimi Prime
https://old.example.com/live/qa-nimi-prime.m3u8
"""
    incoming = """#EXTM3U
#EXTINF:-1,QA Nimi Prime!!!
https://new.example.com/live/qa-nimi-prime.m3u8
#EXTINF:-1,QA Nimi Extra
https://new.example.com/live/qa-nimi-extra.m3u8
"""
    NIMI_FILE.write_text(base, encoding="utf-8")

    files = {"playlist": ("nimi-qa.m3u", incoming.encode("utf-8"), "audio/x-mpegurl")}
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

    merged = _parse_m3u_urls_by_normalized_name(NIMI_FILE.read_text(encoding="utf-8"))
    key = "qa nimi prime"
    assert key in merged
    assert merged[key][0] == new_url
    assert old_url in merged[key][1:]
    assert extra_url in NIMI_FILE.read_text(encoding="utf-8")


def test_open_source_upload_merges_duplicate_and_sets_new_primary(admin_session):
    old_url = "https://old.example.com/live/qa-os-alpha.m3u8"
    new_url = "https://new.example.com/live/qa-os-alpha.m3u8"
    extra_url = "https://new.example.com/live/qa-os-beta.m3u8"

    base = """#EXTM3U
#EXTINF:-1,QA Open Source Alpha
https://old.example.com/live/qa-os-alpha.m3u8
"""
    incoming = """#EXTM3U
#EXTINF:-1,qa open source alpha
https://new.example.com/live/qa-os-alpha.m3u8
#EXTINF:-1,QA Open Source Beta
https://new.example.com/live/qa-os-beta.m3u8
"""
    OPEN_SOURCE_FILE.write_text(base, encoding="utf-8")

    files = {"playlist": ("open-source-qa.m3u", incoming.encode("utf-8"), "audio/x-mpegurl")}
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

    merged = _parse_m3u_urls_by_normalized_name(OPEN_SOURCE_FILE.read_text(encoding="utf-8"))
    key = "qa open source alpha"
    assert key in merged
    assert merged[key][0] == new_url
    assert old_url in merged[key][1:]
    assert extra_url in OPEN_SOURCE_FILE.read_text(encoding="utf-8")


# Channel update API behavior for custom + source-upload channels
def test_channel_update_works_for_custom_and_source_upload(admin_session):
    _refresh(admin_session)
    all_channels = admin_session.get(f"{BASE_URL}/admin/categories/all-channels", timeout=30)
    assert all_channels.status_code == 200
    chmap = _parse_chmap_from_html(all_channels.text)

    custom_slug = ""
    for slug, row in chmap.items():
        if str(row.get("sourceKey") or "").startswith("custom_"):
            custom_slug = slug
            break
    if not custom_slug:
        _ensure_custom_channel_exists(admin_session)
        all_channels = admin_session.get(f"{BASE_URL}/admin/categories/all-channels", timeout=30)
        chmap = _parse_chmap_from_html(all_channels.text)
        for slug, row in chmap.items():
            if str(row.get("sourceKey") or "").startswith("custom_"):
                custom_slug = slug
                break
    assert custom_slug, "No custom channel found for update test"

    custom_update = admin_session.post(
        f"{BASE_URL}/api/admin/channel/update",
        json={
            "slug": custom_slug,
            "name": "QA Custom Updated",
            "url": "https://new.example.com/live/qa-custom-updated.m3u8",
            "backup_urls": ["https://backup.example.com/live/qa-custom-backup.m3u8"],
        },
        timeout=30,
    )
    assert custom_update.status_code == 200
    custom_json = custom_update.json()
    assert custom_json.get("ok") is True
    assert custom_json.get("mode") == "custom_direct"

    custom_data = _read_json(CUSTOM_CH_FILE, {"tv": [], "sport": [], "nimi": []})
    all_custom = [*custom_data.get("tv", []), *custom_data.get("sport", []), *custom_data.get("nimi", [])]
    updated_custom = [row for row in all_custom if _slugify(row.get("slug")) == custom_slug]
    assert updated_custom
    assert updated_custom[0].get("name") == "QA Custom Updated"
    assert updated_custom[0].get("url") == "https://new.example.com/live/qa-custom-updated.m3u8"

    source_slug = _find_slug(chmap, "open_source", "QA Open Source Alpha")
    assert source_slug, "Open-source channel not found for source update test"
    source_update = admin_session.post(
        f"{BASE_URL}/api/admin/channel/update",
        json={
            "slug": source_slug,
            "name": "QA Open Source Alpha",
            "url": "https://new.example.com/live/qa-os-alpha-updated.m3u8",
            "backup_urls": ["https://backup.example.com/live/qa-os-alpha-backup.m3u8"],
        },
        timeout=30,
    )
    assert source_update.status_code == 200
    source_json = source_update.json()
    assert source_json.get("ok") is True
    assert source_json.get("mode") == "source_playlist_direct"

    merged = _parse_m3u_urls_by_normalized_name(OPEN_SOURCE_FILE.read_text(encoding="utf-8"))
    key = "qa open source alpha"
    assert key in merged
    assert merged[key][0] == "https://new.example.com/live/qa-os-alpha-updated.m3u8"
    assert "https://backup.example.com/live/qa-os-alpha-backup.m3u8" in merged[key][1:]


# Hard delete behavior + purge references/history/metadata
def test_channel_delete_hard_deletes_and_purges_references(admin_session):
    _refresh(admin_session)
    all_channels = admin_session.get(f"{BASE_URL}/admin/categories/all-channels", timeout=30)
    chmap = _parse_chmap_from_html(all_channels.text)

    target_slug = ""
    target_name = ""
    for slug, row in chmap.items():
        if str(row.get("sourceKey") or "").startswith("custom_"):
            target_slug = slug
            target_name = str(row.get("name") or "")
            break
    if not target_slug:
        _ensure_custom_channel_exists(admin_session, channel_name="QA Purge Target")
        all_channels = admin_session.get(f"{BASE_URL}/admin/categories/all-channels", timeout=30)
        chmap = _parse_chmap_from_html(all_channels.text)
        for slug, row in chmap.items():
            if str(row.get("sourceKey") or "").startswith("custom_"):
                target_slug = slug
                target_name = str(row.get("name") or "")
                break
    assert target_slug, "No custom channel available for hard-delete test"

    stamp = str(int(time.time()))
    category_name = f"TEST PURGE CAT {stamp}"
    category_id = _slugify(category_name)
    playlist_id = f"test-purge-pl-{stamp}"

    create_cat = admin_session.post(
        f"{BASE_URL}/admin/categories",
        data={"action": "create", "category_name": category_name},
        allow_redirects=False,
        timeout=30,
    )
    assert create_cat.status_code in (302, 303)

    save_cat = admin_session.post(
        f"{BASE_URL}/admin/categories/{category_id}",
        data={
            "show_on_main": "1",
            "selected_count": "1",
            "category_channels": target_slug,
            "channel_order": json.dumps([target_slug]),
        },
        allow_redirects=False,
        timeout=30,
    )
    assert save_cat.status_code in (302, 303)

    create_playlist = admin_session.post(
        f"{BASE_URL}/admin/playlists",
        data={
            "action": "create",
            "playlist_name": f"TEST Purge Playlist {stamp}",
            "playlist_slug": playlist_id,
            "playlist_channels": target_slug,
        },
        allow_redirects=False,
        timeout=30,
    )
    assert create_playlist.status_code in (302, 303)

    # Create "views" + playlist analytics references in runtime state.
    ping = admin_session.post(
        f"{BASE_URL}/api/ping",
        json={"session_id": f"qa_sess_{stamp}", "channel_slug": target_slug, "channel_name": target_name},
        timeout=30,
    )
    assert ping.status_code == 200
    playlist_click = admin_session.get(f"{BASE_URL}/playlist/{playlist_id}/{target_slug}.m3u8", allow_redirects=False, timeout=30)
    assert playlist_click.status_code in (302, 404)

    delete_response = admin_session.post(
        f"{BASE_URL}/api/admin/channel/delete",
        json={"slug": target_slug, "purge": True},
        timeout=30,
    )
    assert delete_response.status_code == 200
    delete_json = delete_response.json()
    assert delete_json.get("ok") is True
    assert delete_json.get("mode") == "hard_delete"

    _refresh(admin_session)
    post_delete_page = admin_session.get(f"{BASE_URL}/admin/categories/all-channels", timeout=30)
    post_map = _parse_chmap_from_html(post_delete_page.text)
    assert target_slug not in post_map

    config = _read_json(CHANNEL_CONFIG_FILE, {})
    assert target_slug not in (config.get("visible_channels") or {})

    categories = _read_json(CUSTOM_CATEGORIES_FILE, {})
    assert target_slug not in (categories.get(category_id, {}).get("channels") or [])

    playlists = _read_json(CUSTOM_PLAYLISTS_FILE, {})
    assert target_slug not in (playlists.get(playlist_id, {}).get("channels") or [])

    channel_views = _read_json(CHANNEL_VIEWS_FILE, {})
    assert target_slug not in channel_views

    playlist_analytics = _read_json(PLAYLIST_ANALYTICS_FILE, {})
    pl_row = playlist_analytics.get(playlist_id, {}) if isinstance(playlist_analytics, dict) else {}
    events = pl_row.get("recent_events") or []
    assert all(str(event.get("target") or "") != target_slug for event in events)


def test_channel_delete_works_for_open_source_and_nimi_channels(admin_session):
    OPEN_SOURCE_FILE.write_text(
        """#EXTM3U
#EXTINF:-1,QA Delete Open Source
https://new.example.com/live/qa-delete-open-source.m3u8
""",
        encoding="utf-8",
    )
    NIMI_FILE.write_text(
        """#EXTM3U
#EXTINF:-1,QA Delete Nimi
https://new.example.com/live/qa-delete-nimi.m3u8
""",
        encoding="utf-8",
    )
    _refresh(admin_session)

    page = admin_session.get(f"{BASE_URL}/admin/categories/all-channels", timeout=30)
    chmap = _parse_chmap_from_html(page.text)
    open_slug = _find_slug(chmap, "open_source", "QA Delete Open Source")
    nimi_slug = _find_slug(chmap, "nimi", "QA Delete Nimi")
    assert open_slug
    assert nimi_slug

    delete_open = admin_session.post(
        f"{BASE_URL}/api/admin/channel/delete", json={"slug": open_slug, "purge": True}, timeout=30
    )
    assert delete_open.status_code == 200
    assert delete_open.json().get("ok") is True

    delete_nimi = admin_session.post(
        f"{BASE_URL}/api/admin/channel/delete", json={"slug": nimi_slug, "purge": True}, timeout=30
    )
    assert delete_nimi.status_code == 200
    assert delete_nimi.json().get("ok") is True

    assert "QA Delete Open Source" not in OPEN_SOURCE_FILE.read_text(encoding="utf-8")
    assert "QA Delete Nimi" not in NIMI_FILE.read_text(encoding="utf-8")


# Private playlist endpoint + stream alias auth behavior
def test_private_playlist_endpoint_503_when_private_env_missing_or_401_when_configured():
    response = requests.get(f"{BASE_URL}/private-playlist/test.m3u", timeout=20, allow_redirects=False)
    assert response.status_code in (503, 401)
    if response.status_code == 503:
        assert "Private playlist auth" in response.text


def test_private_playlist_alias_endpoints_require_basic_auth_or_return_503():
    no_auth = requests.get(f"{BASE_URL}/private-playlist/test/slug.m3u8", timeout=20, allow_redirects=False)
    assert no_auth.status_code in (503, 401)
    if no_auth.status_code == 401:
        assert "WWW-Authenticate" in no_auth.headers
        wrong_auth = requests.get(
            f"{BASE_URL}/private-playlist/test/vod/sample.m3u8",
            timeout=20,
            allow_redirects=False,
            auth=("wrong", "wrong"),
        )
        assert wrong_auth.status_code == 401
