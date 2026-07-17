#!/usr/bin/env python3
"""Hotfix: channels not clickable + admin/features 500."""
from __future__ import annotations

from pathlib import Path

SERVER = Path("/root/all/all2/server.js")
RUNTIME = Path("/root/all/all2/views/player/runtimeConfig.js")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"FAIL {label}: expected 1, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    t = SERVER.read_text(encoding="utf-8")

    # 1) Client-safe webUiConfig (no server-only normalizeWebUiConfig/appConfig)
    old_cfg = (
        "const webUiConfig=(function(){const fromPage=(pageOptions&&pageOptions.webUi&&typeof pageOptions.webUi==='object')"
        "?pageOptions.webUi:null;const fromApp=normalizeWebUiConfig((appConfig&&appConfig.web_ui)||{});"
        "const src=fromPage||fromApp;return {theme:src.theme||'classic',"
        "playerStatusText:String(src.player_status_text||src.playerStatusText||'').trim()};})();"
    )
    new_cfg = (
        "const webUiConfig=(function(){"
        "const src=(pageOptions&&pageOptions.webUi&&typeof pageOptions.webUi==='object')?pageOptions.webUi:{};"
        "return {theme:String(src.theme||'classic'),"
        "playerStatusText:String(src.playerStatusText||src.player_status_text||'').trim()};"
        "})();"
    )
    t = replace_once(t, old_cfg, new_cfg, "webUiConfig client-safe")

    # 2) Inject webUi into pageOptions inside playerScript
    old_ps = """  const siteOwnership = normalizeSiteOwnershipConfig(appConfig.site_ownership);
  const gaMeasurementId = normalizeGoogleAnalyticsMeasurementId(siteOwnership.ga_measurement_id || '');
  return `"""
    new_ps = """  const webUi = normalizeWebUiConfig(options.webUi !== undefined ? options.webUi : appConfig.web_ui);
  normalizedScriptOptions.webUi = {
    theme: webUi.theme || 'classic',
    playerStatusText: webUi.player_status_text || '',
    player_status_text: webUi.player_status_text || ''
  };
  const siteOwnership = normalizeSiteOwnershipConfig(appConfig.site_ownership);
  const gaMeasurementId = normalizeGoogleAnalyticsMeasurementId(siteOwnership.ga_measurement_id || '');
  return `"""
    t = replace_once(t, old_ps, new_ps, "inject webUi into pageOptions")

    # 3) Features page: escapeAttr → escapeHtmlAttr
    t = replace_once(
        t,
        'value="${escapeAttr(getWebUiSettings().player_status_text || \'\')}"',
        'value="${escapeHtmlAttr(getWebUiSettings().player_status_text || \'\')}"',
        "escapeAttr → escapeHtmlAttr",
    )

    SERVER.write_text(t, encoding="utf-8")
    print("server.js fixed")


if __name__ == "__main__":
    main()
