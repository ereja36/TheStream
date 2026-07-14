#!/usr/bin/env python3
"""Hotfix on VPS: absolute logos, Lokke-AL tags, web object-fit contain."""
from pathlib import Path
import json
import re
import shutil
from datetime import datetime

ROOT = Path('/root/all/all2')

# 1) Copy updated services
for name in ['tvLogoService.js', 'channelVariantMerge.js', 'albService.js']:
    src = Path(f'/tmp/k1-deploy/services/{name}')
    if src.exists():
        shutil.copy2(src, ROOT / 'services' / name)
        print('copied', name)

# 2) Patch server.js publicBase + object-fit cover on channel thumbs
server = ROOT / 'server.js'
text = server.read_text(encoding='utf-8')
bak = server.with_suffix(server.suffix + f'.bak-logoabs-{datetime.now().strftime("%Y%m%d-%H%M%S")}')
shutil.copy2(server, bak)

replacements = [
    (
        "  publicBase: `${BASE_PATH}/tv-logos`",
        "  publicBase: 'https://shifshqip.com/k1/tv-logos'"
    ),
    (
        '? `<img src="${safeLogo}" alt="${ch.name}" style="width:100%;height:100%;object-fit:cover" onerror="this.remove()">`',
        '? `<img src="${safeLogo}" alt="${ch.name}" style="width:100%;height:100%;object-fit:contain;padding:10px;background:rgba(255,255,255,.03)" onerror="this.remove()">`'
    ),
]
for old, new in replacements:
    if old in text:
        text = text.replace(old, new)
        print('server patched:', old[:60])
    elif new in text:
        print('already:', old[:40])
    else:
        print('MISSING:', old[:80])

# Absolutize logos in M3U builder inline
old_m3u = '    const logo = String(ch.logo || \'\').trim() || groupLogo;'
new_m3u = '''    let logo = String(ch.logo || '').trim() || groupLogo;
    if (logo && logo.startsWith('/')) logo = `https://shifshqip.com${logo}`;'''
if old_m3u in text and 'logo.startsWith(\'/\')' not in text:
    text = text.replace(old_m3u, new_m3u)
    print('m3u logo absolutize patched')
else:
    print('m3u logo patch skip/already')

server.write_text(text, encoding='utf-8')

# 3) Retag lokke names + absolute logos in caches
codes = {
  'albania':'AL','germany':'DE','italy':'IT','france':'FR','united kingdom':'UK',
  'spain':'ES','turkey':'TR','netherlands':'NL','poland':'PL','portugal':'PT',
  'romania':'RO','russia':'RU','croatia':'HR','serbia':'RS','bulgaria':'BG',
  'greece':'GR','belgium':'BE','austria':'AT','switzerland':'CH'
}

def absolutize(logo: str) -> str:
    logo = (logo or '').strip()
    if not logo:
        return ''
    if logo.startswith('http://') or logo.startswith('https://'):
        # rewrite relative-style host if already absolute with path only mistakes
        return logo
    if logo.startswith('/'):
        return 'https://shifshqip.com' + logo
    if logo.startswith('k1/'):
        return 'https://shifshqip.com/' + logo
    return logo

# Load node logo service via subprocess later; for now retag names + absolutize existing
for fname in ['kool_lokke_channels_cache.json', 'api_tv_channels_cache.json', 'api_sport_channels_cache.json']:
    path = ROOT / 'data' / fname
    data = json.loads(path.read_text())
    changed = 0
    for ch in data.values():
        if not isinstance(ch, dict):
            continue
        name = ch.get('name') or ''
        if 'Lokke' in name or fname.startswith('kool_lokke'):
            cat = (ch.get('koolCategory') or '').split('➾')[0].strip().lower()
            code = codes.get(cat, '')
            base = re.sub(r'\s*\[Lokke(?:[,\-][A-Za-z]{0,3})?\]\s*$', '', name, flags=re.I).strip()
            if fname.startswith('kool_lokke') or '[Lokke' in name:
                tag = f'[Lokke-{code}]' if code else '[Lokke]'
                new_name = f'{base} {tag}'.strip()
                if new_name != name:
                    ch['name'] = new_name
                    changed += 1
        logo = absolutize(ch.get('logo') or '')
        # also rewrite /k1/ relative stored logos
        if (ch.get('logo') or '').startswith('/k1/'):
            ch['logo'] = absolutize(ch['logo'])
            changed += 1
        elif logo and logo != (ch.get('logo') or ''):
            ch['logo'] = logo
            changed += 1
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    print(fname, 'name/logo fixes', changed)

# 4) K Sport logos = copy supersport
alb = Path('/var/www/shifshqip-k1-static/tv-logos/countries/albania')
pub = ROOT / 'public' / 'tv-logos' / 'countries' / 'albania'
for i in range(1, 5):
    src = alb / f'supersport-{i}-al.png'
    for dest_root in (alb, pub):
        if src.exists():
            dest = dest_root / f'k-sport-{i}-al.png'
            shutil.copy2(src, dest)
            print('k-sport logo', dest)

print('DONE patch')
