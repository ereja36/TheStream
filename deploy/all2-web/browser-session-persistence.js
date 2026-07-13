(() => {
  'use strict';

  const config = (() => {
    const src = (window.__K1_BROWSER_SESSION_CONFIG && typeof window.__K1_BROWSER_SESSION_CONFIG === 'object')
      ? window.__K1_BROWSER_SESSION_CONFIG
      : {};
    const normalizeStringArray = (value) => Array.isArray(value)
      ? value.map((item) => String(item || '').trim()).filter(Boolean)
      : [];
    return {
      registerEndpoint: String(src.registerEndpoint || '/k1/api/browser-session/register').trim(),
      browserIdStorageKey: String(src.browserIdStorageKey || 'k1_browser_persistent_id').trim(),
      consentStorageKey: String(src.consentStorageKey || 'k1_session_persistence_opt_in').trim(),
      requireExplicitConsent: src.requireExplicitConsent === true,
      localStoragePreferenceKeys: normalizeStringArray(src.localStoragePreferenceKeys),
      localStoragePreferencePrefixes: normalizeStringArray(src.localStoragePreferencePrefixes),
      maxPreferenceItems: Math.min(Math.max(parseInt(src.maxPreferenceItems, 10) || 20, 1), 80),
      syncIntervalMs: Math.min(Math.max(parseInt(src.syncIntervalMs, 10) || 5 * 60 * 1000, 30 * 1000), 60 * 60 * 1000)
    };
  })();

  const runtimeState = {
    sessionData: {},
    syncing: false,
    syncTimer: null
  };

  function safeLocalStorageGet(key) {
    try {
      return window.localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  function safeLocalStorageSet(key, value) {
    try {
      window.localStorage.setItem(key, value);
      return true;
    } catch {
      return false;
    }
  }

  function randomToken() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
      return window.crypto.randomUUID().replace(/-/g, '');
    }
    const rnd = Math.random().toString(36).slice(2);
    return `${Date.now().toString(36)}${rnd}`;
  }

  function getOrCreateBrowserId() {
    const existing = String(safeLocalStorageGet(config.browserIdStorageKey) || '').trim();
    if (existing) return existing;
    const created = `br_${randomToken().slice(0, 48)}`;
    safeLocalStorageSet(config.browserIdStorageKey, created);
    return created;
  }

  function hasConsent() {
    if (!config.requireExplicitConsent) return true;
    return safeLocalStorageGet(config.consentStorageKey) === '1';
  }

  function collectFingerprint() {
    const width = Number(window.screen && window.screen.width) || 0;
    const height = Number(window.screen && window.screen.height) || 0;
    return {
      user_agent: String(window.navigator.userAgent || ''),
      screen_resolution: `${Math.max(0, width)}x${Math.max(0, height)}`,
      timezone: String(Intl.DateTimeFormat().resolvedOptions().timeZone || ''),
      language: String(window.navigator.language || ''),
      platform: String(window.navigator.platform || '')
    };
  }

  function collectPreferenceItems() {
    const out = {};
    const directKeys = new Set(config.localStoragePreferenceKeys);
    const prefixes = config.localStoragePreferencePrefixes;

    for (const key of directKeys) {
      const value = safeLocalStorageGet(key);
      if (value !== null) out[key] = value;
    }

    if (prefixes.length) {
      try {
        let count = Object.keys(out).length;
        for (let i = 0; i < window.localStorage.length; i += 1) {
          const key = String(window.localStorage.key(i) || '');
          if (!key || out[key] !== undefined) continue;
          if (!prefixes.some((prefix) => key.startsWith(prefix))) continue;
          const value = safeLocalStorageGet(key);
          if (value !== null) out[key] = value;
          count += 1;
          if (count >= config.maxPreferenceItems) break;
        }
      } catch {}
    }

    return out;
  }

  function parseJsonSafe(value, fallback) {
    try {
      return JSON.parse(value);
    } catch {
      return fallback;
    }
  }

  function applyRestoredPreferences(restored) {
    if (!restored || typeof restored !== 'object') return;
    for (const [key, value] of Object.entries(restored)) {
      if (!key) continue;
      if (safeLocalStorageGet(key) !== null) continue;
      safeLocalStorageSet(key, String(value));
    }

    // Keep favorites UI in sync if this app has those helpers loaded.
    if (typeof window.updateFavSection === 'function') {
      try { window.updateFavSection(); } catch {}
    }
  }

  async function sendPayload(payload) {
    const body = JSON.stringify(payload);
    const res = await fetch(config.registerEndpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
      keepalive: true
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.json().catch(() => ({}));
  }

  async function syncNow() {
    if (!hasConsent() || runtimeState.syncing) return { ok: false, skipped: 'consent_or_busy' };
    runtimeState.syncing = true;
    try {
      const browserId = getOrCreateBrowserId();
      const payload = {
        browser_id: browserId,
        fingerprint: collectFingerprint(),
        session_data: {
          ...runtimeState.sessionData,
          page_path: String(window.location.pathname || ''),
          referrer: String(document.referrer || ''),
          runtime_session_id: String(window.sessionStorage.getItem('k1_runtime_sid') || '')
        },
        local_storage_items: collectPreferenceItems()
      };
      const data = await sendPayload(payload);
      if (data && data.ok) {
        if (typeof data.browser_id === 'string' && data.browser_id.trim()) {
          safeLocalStorageSet(config.browserIdStorageKey, data.browser_id.trim());
        }
        if (data.restored_preferences && typeof data.restored_preferences === 'object') {
          applyRestoredPreferences(data.restored_preferences);
        }
      }
      return data || { ok: true };
    } catch (error) {
      return { ok: false, error: String(error && error.message ? error.message : error) };
    } finally {
      runtimeState.syncing = false;
    }
  }

  function startAutoSync() {
    if (runtimeState.syncTimer) return;
    runtimeState.syncTimer = window.setInterval(() => {
      syncNow().catch(() => {});
    }, config.syncIntervalMs);
  }

  function init() {
    if (window.__K1_BROWSER_SESSION_READY === true) return;
    window.__K1_BROWSER_SESSION_READY = true;

    // Track a stable runtime session token in sessionStorage for correlation.
    const runtimeSid = String(window.sessionStorage.getItem('k1_runtime_sid') || '').trim();
    if (!runtimeSid) {
      window.sessionStorage.setItem('k1_runtime_sid', `rt_${randomToken().slice(0, 36)}`);
    }

    syncNow().catch(() => {});
    startAutoSync();
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') syncNow().catch(() => {});
    });
  }

  window.K1BrowserSessionPersistence = {
    init,
    syncNow,
    setSessionData(data) {
      if (!data || typeof data !== 'object') return;
      runtimeState.sessionData = { ...runtimeState.sessionData, ...data };
    },
    grantConsent() {
      safeLocalStorageSet(config.consentStorageKey, '1');
      return syncNow();
    },
    revokeConsent() {
      safeLocalStorageSet(config.consentStorageKey, '0');
    },
    getBrowserId() {
      return getOrCreateBrowserId();
    },
    getSnapshot() {
      return {
        browser_id: getOrCreateBrowserId(),
        fingerprint: collectFingerprint(),
        local_storage_items: collectPreferenceItems(),
        session_data: parseJsonSafe(JSON.stringify(runtimeState.sessionData), {})
      };
    }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
