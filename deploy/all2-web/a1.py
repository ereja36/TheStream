from flask import Flask, Response, request, render_template_string
import requests
import hashlib
import hmac
import threading
import time
import os
import re
import base64
import urllib3
from concurrent.futures import ThreadPoolExecutor

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

app = Flask(__name__)

PORT = 1221

# ─── AES-128-CBC + HMAC-SHA256 (stdlib only, zero external packages) ──────────
# Token = base64url( IV[16] | CIPHERTEXT | HMAC[32] )
# URL is fully encrypted — sniffing ?id= reveals nothing readable.

_KEY      = hashlib.sha256(b"Strong_IPTV_Supreme_2026_Secure_Key_512").digest()
_HMAC_KEY = hashlib.sha256(b"HMAC_" + _KEY).digest()
_AES_KEY  = _KEY[:16]   # AES-128 (faster on Termux, still unbreakable)

# ── Pure-Python AES S-box / inverse ───────────────────────────────────────────
_S = [
    0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
    0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
    0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
    0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
    0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
    0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
    0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
    0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
    0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
    0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
    0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
    0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
    0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
    0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
    0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
    0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16,
]
_IS = [0]*256
for _i, _v in enumerate(_S): _IS[_v] = _i

def _mul(a, b):
    r = 0
    for _ in range(8):
        if b & 1: r ^= a
        hi = a & 0x80; a = (a << 1) & 0xff
        if hi: a ^= 0x1b
        b >>= 1
    return r

def _expand_key(key):
    k = list(key); nk = len(k) // 4; w = list(k)
    rcon = [0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36]
    for i in range(nk, 4*(nk+7)):
        t = list(w[(i-1)*4:i*4])
        if i % nk == 0:
            t = [_S[t[1]]^rcon[i//nk-1], _S[t[2]], _S[t[3]], _S[t[0]]]
        elif nk > 6 and i % nk == 4:
            t = [_S[b] for b in t]
        w += [w[(i-nk)*4+j]^t[j] for j in range(4)]
    return w   # flat list

def _rk(w, r): return w[r*16:(r+1)*16]

def _ark(state, rk):   return [a^b for a,b in zip(state, rk)]
def _sub(s):           return [_S[b] for b in s]
def _isub(s):          return [_IS[b] for b in s]
def _sr(s):
    return [s[0],s[5],s[10],s[15],s[4],s[9],s[14],s[3],
            s[8],s[13],s[2],s[7],s[12],s[1],s[6],s[11]]
def _isr(s):
    return [s[0],s[13],s[10],s[7],s[4],s[1],s[14],s[11],
            s[8],s[5],s[2],s[15],s[12],s[9],s[6],s[3]]
def _mc(c):
    return [_mul(c[0],2)^_mul(c[1],3)^c[2]^c[3],
            c[0]^_mul(c[1],2)^_mul(c[2],3)^c[3],
            c[0]^c[1]^_mul(c[2],2)^_mul(c[3],3),
            _mul(c[0],3)^c[1]^c[2]^_mul(c[3],2)]
def _imc(c):
    return [_mul(c[0],14)^_mul(c[1],11)^_mul(c[2],13)^_mul(c[3],9),
            _mul(c[0],9)^_mul(c[1],14)^_mul(c[2],11)^_mul(c[3],13),
            _mul(c[0],13)^_mul(c[1],9)^_mul(c[2],14)^_mul(c[3],11),
            _mul(c[0],11)^_mul(c[1],13)^_mul(c[2],9)^_mul(c[3],14)]
def _mr(s, fn):
    o = []
    for col in range(4): o += fn(s[col*4:col*4+4])
    return o

def _aes_enc(key, block):
    w = _expand_key(key); nr = len(key)//4 + 6
    s = _ark(list(block), _rk(w, 0))
    for r in range(1, nr):
        s = _sub(s); s = _sr(s); s = _mr(s, _mc); s = _ark(s, _rk(w, r))
    s = _sub(s); s = _sr(s); s = _ark(s, _rk(w, nr))
    return bytes(s)

def _aes_dec(key, block):
    w = _expand_key(key); nr = len(key)//4 + 6
    s = _ark(list(block), _rk(w, nr))
    for r in range(nr-1, 0, -1):
        s = _isr(s); s = _isub(s); s = _ark(s, _rk(w, r)); s = _mr(s, _imc)
    s = _isr(s); s = _isub(s); s = _ark(s, _rk(w, 0))
    return bytes(s)

def _pad(d):  p = 16-(len(d)%16); return d+bytes([p]*p)
def _unpad(d):
    p = d[-1]
    if p < 1 or p > 16 or d[-p:] != bytes([p]*p): raise ValueError
    return d[:-p]

def encrypt_link(url: str) -> str:
    iv = os.urandom(16)
    padded = _pad(url.encode())
    ct = b""; prev = iv
    for i in range(0, len(padded), 16):
        blk = bytes(a^b for a,b in zip(padded[i:i+16], prev))
        enc = _aes_enc(_AES_KEY, blk); ct += enc; prev = enc
    mac = hmac.new(_HMAC_KEY, iv+ct, hashlib.sha256).digest()
    return base64.urlsafe_b64encode(iv+ct+mac).decode().rstrip("=")

def decrypt_link(token: str):
    try:
        raw = base64.urlsafe_b64decode(token + "=" * (-len(token) % 4))
        if len(raw) < 64: return None
        iv, ct, mac = raw[:16], raw[16:-32], raw[-32:]
        if not hmac.compare_digest(hmac.new(_HMAC_KEY,iv+ct,hashlib.sha256).digest(), mac):
            return None
        pt = b""; prev = iv
        for i in range(0, len(ct), 16):
            dec = _aes_dec(_AES_KEY, ct[i:i+16])
            pt += bytes(a^b for a,b in zip(dec, prev)); prev = ct[i:i+16]
        return _unpad(pt).decode()
    except Exception:
        return None

all_channels_db = {}
ts_cache = {}
cache_lock = threading.Lock()
executor = ThreadPoolExecutor(max_workers=20)

# ─── Slug ─────────────────────────────────────────────────────────────────────

def create_slug(name, url):
    slug_base = re.sub(r'[^\w\s-]', '', str(name).lower())
    slug_base = re.sub(r'[-\s]+', '_', slug_base).strip('_')
    return f"{slug_base}_{hashlib.md5(url.encode()).hexdigest()[:6]}"

# ─── Cache cleanup ─────────────────────────────────────────────────────────────

def cleanup_cache():
    while True:
        time.sleep(30)
        now = time.time()
        with cache_lock:
            expired = [u for u, d in ts_cache.items() if now - d['time'] > 45]
            for u in expired:
                del ts_cache[u]

threading.Thread(target=cleanup_cache, daemon=True).start()

# ─── Load channels ─────────────────────────────────────────────────────────────

def load_channels():
    global all_channels_db
    temp_db = {}
    base_dir = os.path.dirname(os.path.abspath(__file__))
    file_path = os.path.join(base_dir, "nimi_channels.m3u")

    if os.path.exists(file_path):
        with open(file_path, "r", encoding="utf-8") as f:
            name = ""
            logo = ""
            group = ""
            for line in f:
                line = line.strip()
                if line.startswith("#EXTINF"):
                    name = line.split(",")[-1].strip()
                    logo_match = re.search(r'tvg-logo="([^"]*)"', line)
                    group_match = re.search(r'group-title="([^"]*)"', line)
                    logo = logo_match.group(1) if logo_match else ""
                    group = group_match.group(1) if group_match else "General"
                elif line.startswith("http"):
                    if name:
                        slug = create_slug(name, line)
                        temp_db[slug] = {
                            "name": name,
                            "url": line,
                            "logo": logo,
                            "group": group,
                        }
                        name = logo = group = ""

    all_channels_db = temp_db
    print(f"[INFO] Loaded {len(all_channels_db)} channels.")

load_channels()

# ─── Web Player HTML ────────────────────────────────────────────────────────────

WEB_PLAYER_HTML = """<!DOCTYPE html>
<html lang="sq">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>IPTV Player</title>
<link href="https://fonts.googleapis.com/css2?family=Bebas+Neue&family=DM+Sans:wght@300;400;500&display=swap" rel="stylesheet"/>
<script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
<style>
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

  :root {
    --bg: #0a0a0f;
    --surface: #12121a;
    --card: #1a1a26;
    --border: #ffffff10;
    --accent: #e63946;
    --accent2: #ff6b6b;
    --text: #f0f0f5;
    --muted: #6b6b80;
    --radius: 10px;
  }

  body {
    background: var(--bg);
    color: var(--text);
    font-family: 'DM Sans', sans-serif;
    min-height: 100vh;
    display: flex;
    flex-direction: column;
  }

  header {
    padding: 18px 30px;
    border-bottom: 1px solid var(--border);
    display: flex;
    align-items: center;
    gap: 14px;
    background: var(--surface);
  }
  header h1 {
    font-family: 'Bebas Neue', sans-serif;
    font-size: 1.8rem;
    letter-spacing: 3px;
    color: var(--accent);
  }
  header span { color: var(--muted); font-size: 0.85rem; }

  .layout {
    display: flex;
    flex: 1;
    overflow: hidden;
    height: calc(100vh - 65px);
  }

  /* ── Sidebar ── */
  .sidebar {
    width: 300px;
    min-width: 260px;
    background: var(--surface);
    border-right: 1px solid var(--border);
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .search-wrap {
    padding: 14px 14px 10px;
    border-bottom: 1px solid var(--border);
  }
  .search-wrap input {
    width: 100%;
    background: var(--card);
    border: 1px solid var(--border);
    color: var(--text);
    padding: 9px 14px;
    border-radius: var(--radius);
    font-family: 'DM Sans', sans-serif;
    font-size: 0.88rem;
    outline: none;
    transition: border-color 0.2s;
  }
  .search-wrap input:focus { border-color: var(--accent); }
  .search-wrap input::placeholder { color: var(--muted); }

  .groups {
    display: flex;
    gap: 6px;
    padding: 8px 14px;
    flex-wrap: wrap;
    border-bottom: 1px solid var(--border);
  }
  .group-btn {
    background: var(--card);
    border: 1px solid var(--border);
    color: var(--muted);
    padding: 4px 10px;
    border-radius: 20px;
    font-size: 0.75rem;
    cursor: pointer;
    transition: all 0.2s;
    font-family: 'DM Sans', sans-serif;
  }
  .group-btn:hover, .group-btn.active {
    background: var(--accent);
    border-color: var(--accent);
    color: #fff;
  }

  .channel-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px 8px;
    scrollbar-width: thin;
    scrollbar-color: var(--accent) transparent;
  }
  .channel-list::-webkit-scrollbar { width: 4px; }
  .channel-list::-webkit-scrollbar-track { background: transparent; }
  .channel-list::-webkit-scrollbar-thumb { background: var(--accent); border-radius: 4px; }

  .channel-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 9px 10px;
    border-radius: var(--radius);
    cursor: pointer;
    transition: background 0.15s;
    border: 1px solid transparent;
  }
  .channel-item:hover { background: var(--card); }
  .channel-item.active {
    background: var(--card);
    border-color: var(--accent);
  }
  .channel-logo {
    width: 36px;
    height: 36px;
    border-radius: 6px;
    object-fit: contain;
    background: var(--bg);
    flex-shrink: 0;
  }
  .channel-logo-placeholder {
    width: 36px;
    height: 36px;
    border-radius: 6px;
    background: var(--accent);
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: 'Bebas Neue', sans-serif;
    font-size: 1rem;
    color: #fff;
    flex-shrink: 0;
  }
  .channel-name {
    font-size: 0.85rem;
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    color: var(--text);
  }

  /* ── Main ── */
  .main {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: var(--bg);
  }

  .player-wrap {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #000;
    position: relative;
    min-height: 0;
  }

  video {
    width: 100%;
    height: 100%;
    object-fit: contain;
    display: block;
  }

  .placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    color: var(--muted);
  }
  .placeholder svg { opacity: 0.3; }
  .placeholder p { font-size: 1rem; }

  .now-playing {
    padding: 12px 20px;
    background: var(--surface);
    border-top: 1px solid var(--border);
    display: flex;
    align-items: center;
    gap: 12px;
    min-height: 52px;
  }
  .np-dot {
    width: 8px; height: 8px;
    border-radius: 50%;
    background: var(--accent);
    animation: pulse 1.4s infinite;
    flex-shrink: 0;
  }
  @keyframes pulse {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.4; transform: scale(0.7); }
  }
  .np-text { font-size: 0.88rem; color: var(--text); }
  .np-text span { color: var(--muted); }

  /* ── Loading / Error overlay ── */
  .overlay {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #000a;
    color: #fff;
    font-size: 1rem;
    flex-direction: column;
    gap: 10px;
    pointer-events: none;
    opacity: 0;
    transition: opacity 0.3s;
  }
  .overlay.show { opacity: 1; }
  .spinner {
    width: 36px; height: 36px;
    border: 3px solid #fff2;
    border-top-color: var(--accent);
    border-radius: 50%;
    animation: spin 0.7s linear infinite;
  }
  @keyframes spin { to { transform: rotate(360deg); } }

  @media (max-width: 700px) {
    .layout { flex-direction: column; height: auto; }
    .sidebar { width: 100%; height: 260px; border-right: none; border-bottom: 1px solid var(--border); }
    .player-wrap { min-height: 220px; }
  }
</style>
</head>
<body>

<header>
  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#e63946" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
    <polygon points="5 3 19 12 5 21 5 3"/>
  </svg>
  <h1>IPTV</h1>
  <span id="count-label">Duke ngarkuar...</span>
</header>

<div class="layout">
  <aside class="sidebar">
    <div class="search-wrap">
      <input type="text" id="search" placeholder="🔍 Kërko kanal..."/>
    </div>
    <div class="groups" id="groups"></div>
    <div class="channel-list" id="channel-list"></div>
  </aside>

  <section class="main">
    <div class="player-wrap" id="player-wrap">
      <video id="video" controls autoplay></video>
      <div class="overlay" id="overlay">
        <div class="spinner"></div>
        <span id="overlay-msg">Duke u ngarkuar...</span>
      </div>
      <div class="placeholder" id="placeholder">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <rect x="2" y="7" width="20" height="14" rx="2"/>
          <path d="M16 2l-4 5-4-5"/>
        </svg>
        <p>Zgjidh një kanal për të luajtur</p>
      </div>
    </div>
    <div class="now-playing" id="now-playing" style="display:none">
      <div class="np-dot"></div>
      <div class="np-text"><span>Tani luan:</span> <strong id="np-name"></strong></div>
    </div>
  </section>
</div>

<script>
let channels = [];
let hls = null;
let activeSlug = null;
let activeGroup = 'Të gjitha';

async function loadChannels() {
  try {
    const res = await fetch('/api/channels');
    channels = await res.json();
    document.getElementById('count-label').textContent = channels.length + ' kanale';
    buildGroups();
    renderList(channels);
  } catch(e) {
    document.getElementById('count-label').textContent = 'Gabim ngarkimi';
  }
}

function buildGroups() {
  const gs = ['Të gjitha', ...new Set(channels.map(c => c.group || 'General'))];
  const wrap = document.getElementById('groups');
  wrap.innerHTML = gs.map(g =>
    `<button class="group-btn${g === activeGroup ? ' active' : ''}" onclick="filterGroup('${g.replace(/'/g,"\\'")}')">
      ${g}
    </button>`
  ).join('');
}

function filterGroup(g) {
  activeGroup = g;
  document.querySelectorAll('.group-btn').forEach(b => b.classList.toggle('active', b.textContent.trim() === g));
  applyFilters();
}

function applyFilters() {
  const q = document.getElementById('search').value.toLowerCase();
  let list = channels;
  if (activeGroup !== 'Të gjitha') list = list.filter(c => (c.group || 'General') === activeGroup);
  if (q) list = list.filter(c => c.name.toLowerCase().includes(q));
  renderList(list);
}

function renderList(list) {
  const el = document.getElementById('channel-list');
  el.innerHTML = list.map(c => {
    const logo = c.logo
      ? `<img class="channel-logo" src="${c.logo}" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'" loading="lazy"/>
         <div class="channel-logo-placeholder" style="display:none">${c.name[0].toUpperCase()}</div>`
      : `<div class="channel-logo-placeholder">${c.name[0].toUpperCase()}</div>`;
    return `<div class="channel-item${c.slug === activeSlug ? ' active' : ''}" onclick="playChannel('${c.slug}','${c.name.replace(/'/g,"\\'")}')">
      ${logo}
      <span class="channel-name">${c.name}</span>
    </div>`;
  }).join('');
}

function playChannel(slug, name) {
  activeSlug = slug;
  applyFilters();

  const video = document.getElementById('video');
  const placeholder = document.getElementById('placeholder');
  const overlay = document.getElementById('overlay');
  const overlayMsg = document.getElementById('overlay-msg');
  const nowPlaying = document.getElementById('now-playing');
  const npName = document.getElementById('np-name');

  placeholder.style.display = 'none';
  nowPlaying.style.display = 'flex';
  npName.textContent = name;
  overlayMsg.textContent = 'Duke u ngarkuar...';
  overlay.classList.add('show');

  const src = `/channel/${slug}`;

  if (hls) { hls.destroy(); hls = null; }

  if (Hls.isSupported()) {
    hls = new Hls({ enableWorker: true, lowLatencyMode: true, backBufferLength: 30 });
    hls.loadSource(src);
    hls.attachMedia(video);
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      overlay.classList.remove('show');
      video.play().catch(() => {});
    });
    hls.on(Hls.Events.ERROR, (e, data) => {
      if (data.fatal) {
        overlayMsg.textContent = '⚠ Gabim lidhjeje. Duke u riprovuar...';
        overlay.classList.add('show');
        setTimeout(() => playChannel(slug, name), 3000);
      }
    });
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = src;
    video.addEventListener('loadedmetadata', () => overlay.classList.remove('show'), { once: true });
    video.play().catch(() => {});
  } else {
    overlayMsg.textContent = '⚠ Browseri nuk mbështet HLS.';
  }
}

document.getElementById('search').addEventListener('input', applyFilters);
loadChannels();
</script>
</body>
</html>"""

# ─── Routes ────────────────────────────────────────────────────────────────────

@app.route('/')
def main_page():
    return render_template_string(WEB_PLAYER_HTML)


@app.route('/api/channels')
def api_channels():
    from flask import jsonify
    data = [
        {
            "slug": slug,
            "name": ch["name"],
            "logo": ch.get("logo", ""),
            "group": ch.get("group", "General"),
        }
        for slug, ch in all_channels_db.items()
    ]
    return app.response_class(
        response=__import__('json').dumps(data, ensure_ascii=False),
        mimetype='application/json'
    )


@app.route('/channel/<slug>')
def channel_proxy(slug):
    channel = all_channels_db.get(slug)
    if not channel:
        return "404 - Kanali nuk u gjet.", 404

    try:
        r = requests.get(channel['url'], timeout=10, verify=False,
                         headers={"User-Agent": "Mozilla/5.0"})
        r.raise_for_status()
        host = request.host
        new_m3u8 = ""

        for line in r.text.splitlines():
            stripped = line.strip()
            if stripped.startswith("http"):
                encrypted_id = encrypt_link(stripped)
                new_m3u8 += f"http://{host}/proxy_ts?id={encrypted_id}\n"
            else:
                new_m3u8 += line + "\n"

        return Response(new_m3u8, content_type="application/vnd.apple.mpegurl",
                        headers={"Access-Control-Allow-Origin": "*"})
    except Exception as e:
        app.logger.error(f"channel_proxy error for {slug}: {e}")
        return "Gabim serveri.", 500


@app.route('/proxy_ts')
def proxy_ts():
    token = request.args.get('id')
    ts_url = decrypt_link(token)

    if not ts_url:
        return "Bllokuar nga politika e sigurisë.", 403

    download_needed = False

    with cache_lock:
        if ts_url in ts_cache:
            cache_entry = ts_cache[ts_url]
        else:
            cache_entry = {
                'data': None,
                'time': time.time(),
                'event': threading.Event()
            }
            ts_cache[ts_url] = cache_entry
            download_needed = True

    if download_needed:
        def fetch():
            try:
                resp = requests.get(ts_url, timeout=10, verify=False,
                                    stream=True,
                                    headers={"User-Agent": "Mozilla/5.0"})
                cache_entry['data'] = resp.content
            except Exception:
                cache_entry['data'] = b''
            finally:
                cache_entry['event'].set()

        executor.submit(fetch)

    cache_entry['event'].wait(timeout=12)
    data = cache_entry.get('data') or b''

    return Response(data, content_type="video/mp2t",
                    headers={"Access-Control-Allow-Origin": "*"})


@app.route('/playlist')
def generate_playlist():
    host = request.host
    m3u = "#EXTM3U\n"
    for slug, ch in all_channels_db.items():
        logo = ch.get("logo", "")
        group = ch.get("group", "General")
        m3u += (
            f'#EXTINF:-1 tvg-logo="{logo}" group-title="{group}",{ch["name"]}\n'
            f'http://{host}/channel/{slug}\n\n'
        )
    return Response(m3u.encode('utf-8'), content_type='audio/x-mpegurl')


@app.route('/reload')
def reload_channels():
    load_channels()
    return f"U ringarkuan {len(all_channels_db)} kanale."


# ─── Run ───────────────────────────────────────────────────────────────────────

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=PORT, threaded=True)
