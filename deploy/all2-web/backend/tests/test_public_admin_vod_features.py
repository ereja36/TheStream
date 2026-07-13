"""Regression tests for public notifications, stats VOD cards, and VOD analytics progress."""

import os
import time
import uuid

import pytest
import requests


BASE_URL = (os.environ.get("K1_BASE_URL") or "http://127.0.0.1:3000/k1").rstrip("/")
ADMIN_USERNAME = os.environ.get("K1_ADMIN_USERNAME") or "admin"
ADMIN_PASSWORD = os.environ.get("K1_ADMIN_PASSWORD") or "kanaliim2024"


@pytest.fixture()
def admin_session():
    """Authenticated admin session via /login form."""
    session = requests.Session()
    login_response = session.post(
        f"{BASE_URL}/login",
        data={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD},
        allow_redirects=False,
        timeout=20,
    )
    assert login_response.status_code in (302, 303)
    assert "/admin" in login_response.headers.get("location", "")
    return session


def test_public_home_hides_source_folder_section():
    """Main page should not render old Source Folder section."""
    response = requests.get(f"{BASE_URL}/", timeout=20)
    assert response.status_code == 200
    html = response.text
    assert "Source Folder" not in html
    assert "main-custom-categories-section" in html


def test_save_public_notifications_and_reflect_on_public_home(admin_session):
    """Admin can save public notification strip settings and they appear on public home."""
    suffix = str(int(time.time()))
    payload = {
        "pn_enabled": "1",
        "pn_live_enabled": "1",
        "pn_partnership_enabled": "1",
        "pn_ads_enabled": "1",
        "pn_live_title": f"Live {suffix}",
        "pn_live_text": f"Live test {suffix}",
        "pn_live_url": "/k1/",
        "pn_partnership_title": f"Partner {suffix}",
        "pn_partnership_text": f"Partner test {suffix}",
        "pn_partnership_url": "/k1/vod",
        "pn_ads_title": f"Ads {suffix}",
        "pn_ads_text": f"Ads test {suffix}",
        "pn_ads_url": "/k1/",
    }

    save_response = admin_session.post(
        f"{BASE_URL}/admin/push/public-notifications",
        data=payload,
        allow_redirects=False,
        timeout=20,
    )
    assert save_response.status_code in (302, 303)
    assert "/admin/push?success=" in save_response.headers.get("location", "")

    public_response = admin_session.get(f"{BASE_URL}/", timeout=20)
    assert public_response.status_code == 200
    html = public_response.text
    assert payload["pn_live_text"] in html
    assert payload["pn_partnership_text"] in html
    assert payload["pn_ads_text"] in html


def test_stats_api_contains_vod_cards_payload(admin_session):
    """Stats API should include VOD metrics used by admin cards."""
    stats_response = admin_session.get(f"{BASE_URL}/api/stats", timeout=20)
    assert stats_response.status_code == 200

    data = stats_response.json()
    vod = data.get("vod", {})

    assert isinstance(vod.get("views"), (int, float))
    assert isinstance(vod.get("watchTimeSec"), (int, float))
    assert isinstance(vod.get("completions"), (int, float))


def test_ad_snippets_enable_and_multi_rows_persist(admin_session):
    """Ad snippets save should persist enabled=true and multi-row settings."""
    banner_a = f"<div>ROW_A_{int(time.time())}</div>"
    banner_b = "<div>ROW_B</div>"
    banner_c = "<div>ROW_C</div>"
    save_response = admin_session.post(
        f"{BASE_URL}/admin/tools",
        data={
            "action": "save_ad_snippets",
            "adsn_enabled": "1",
            "adsn_ezoic_enabled": "1",
            "adsn_snippet_rotation_mode": "round_robin",
            "adsn_show_banner_main": "1",
            "adsn_show_banner_category": "1",
            "adsn_banner_placement_main": "floating_bottom",
            "adsn_banner_placement_category": "floating_bottom",
            "adsn_banner_multi_enabled": "1",
            "adsn_banner_multi_rows": "2",
            "adsn_banner_frequency_cap_sec": "0",
            "adsn_banner_max_views_per_24h": "0",
            "adsn_banner_snippets_text": f"{banner_a}\n---\n{banner_b}\n---\n{banner_c}",
        },
        allow_redirects=False,
        timeout=20,
    )
    assert save_response.status_code in (302, 303)

    with open("/app/data/channel_config.json", "r", encoding="utf-8") as handle:
        config = __import__("json").load(handle)

    snippets = (config.get("ad_snippets") or {})
    assert snippets.get("enabled") is True
    assert snippets.get("banner_multi_enabled") is True
    assert int(snippets.get("banner_multi_rows") or 0) == 2
    assert snippets.get("snippet_rotation_mode") == "round_robin"
    assert len(snippets.get("banner_html_list") or []) >= 3


def test_vod_progress_uses_delta_and_completion_guard(admin_session):
    """Progress endpoint should add watch time by delta and avoid completion spam."""
    vod_id = f"TEST_vod_{uuid.uuid4().hex[:10]}"
    create_payload = {
        "id": vod_id,
        "title": f"Test Vod {vod_id}",
        "source_url": "https://example.com/stream/test.m3u8",
        "visibility": "public",
        "status": "published",
        "duration": 120,
    }

    create_response = admin_session.post(
        f"{BASE_URL}/api/admin/vod/create", json=create_payload, timeout=20
    )
    assert create_response.status_code == 200
    created = create_response.json()
    assert created.get("ok") is True
    assert created.get("item", {}).get("id") == vod_id

    try:
        view_response = admin_session.post(
            f"{BASE_URL}/api/vod/analytics/view", json={"id": vod_id}, timeout=20
        )
        assert view_response.status_code == 200
        assert view_response.json().get("ok") is True

        progress_1 = admin_session.post(
            f"{BASE_URL}/api/vod/analytics/progress",
            json={"id": vod_id, "watch_time_sec": 30, "completed": False},
            timeout=20,
        )
        assert progress_1.status_code == 200
        analytics_1 = progress_1.json().get("analytics", {})
        watch_1 = int(analytics_1.get("watchTimeSec", 0))
        completions_1 = int(analytics_1.get("completions", 0))

        progress_2 = admin_session.post(
            f"{BASE_URL}/api/vod/analytics/progress",
            json={"id": vod_id, "watch_time_sec": 10, "completed": False},
            timeout=20,
        )
        assert progress_2.status_code == 200
        analytics_2 = progress_2.json().get("analytics", {})
        watch_2 = int(analytics_2.get("watchTimeSec", 0))
        assert watch_2 == watch_1

        progress_3 = admin_session.post(
            f"{BASE_URL}/api/vod/analytics/progress",
            json={"id": vod_id, "watch_time_sec": 50, "completed": True},
            timeout=20,
        )
        assert progress_3.status_code == 200
        analytics_3 = progress_3.json().get("analytics", {})
        watch_3 = int(analytics_3.get("watchTimeSec", 0))
        completions_3 = int(analytics_3.get("completions", 0))
        assert watch_3 == watch_1 + 20
        assert completions_3 == completions_1 + 1

        progress_4 = admin_session.post(
            f"{BASE_URL}/api/vod/analytics/progress",
            json={"id": vod_id, "watch_time_sec": 60, "completed": True},
            timeout=20,
        )
        assert progress_4.status_code == 200
        analytics_4 = progress_4.json().get("analytics", {})
        assert int(analytics_4.get("completions", 0)) == completions_3

    finally:
        delete_response = admin_session.post(
            f"{BASE_URL}/api/admin/vod/delete", json={"id": vod_id}, timeout=20
        )
        assert delete_response.status_code in (200, 404)
