#!/usr/bin/env bash
set -euo pipefail
ROOT=/root/all/all2
cd "$ROOT"

echo "== Upload service files =="
# files expected in /tmp/k1-deploy/

cp -f /tmp/k1-deploy/services/tvLogoService.js "$ROOT/services/tvLogoService.js"
cp -f /tmp/k1-deploy/services/channelVariantMerge.js "$ROOT/services/channelVariantMerge.js"
cp -f /tmp/k1-deploy/services/albService.js "$ROOT/services/albService.js"
cp -f /tmp/k1-deploy/scripts/patch-server-k1-logos.py "$ROOT/scripts/patch-server-k1-logos.py"

python3 "$ROOT/scripts/patch-server-k1-logos.py"

echo "== Clean prod API blocked channels =="
python3 - <<'PY'
import json
from pathlib import Path

def blocked(name: str) -> bool:
    key = ''.join(ch if ch.isalnum() else ' ' for ch in (name or '').lower())
    key = ' '.join(key.split())
    if key.startswith('metitv') or 'meti tv' in key or key == 'meti':
        return True
    if 'mahmutovit' in key or 'rexhepovit' in key:
        return True
    return False

for fname in ['api_tv_channels_cache.json', 'api_sport_channels_cache.json']:
    path = Path('/root/all/all2/data') / fname
    if not path.exists():
        continue
    data = json.loads(path.read_text())
    before = len(data)
    data = {k: v for k, v in data.items() if not (isinstance(v, dict) and blocked(v.get('name') or ''))}
    after = len(data)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    print(f'{fname}: {before} -> {after} (removed {before-after})')

cfg_path = Path('/root/all/all2/data/channel_config.json')
cfg = json.loads(cfg_path.read_text())
keys = list(cfg.get('deleted_channel_keys') or [])
for item in [
    'MetiTV',
    'Meti TV',
    'Mahmutovitët dhe Rexhepovitët',
    'Mahmutovitet dhe Rexhepovitet',
]:
    if item not in keys:
        keys.append(item)
cfg['deleted_channel_keys'] = keys
cfg_path.write_text(json.dumps(cfg, ensure_ascii=False, indent=2) + '\n')
print('deleted_channel_keys updated', keys)
PY

echo "== Retag lokke names in cache =="
python3 - <<'PY'
import json, re
from pathlib import Path
p = Path('/root/all/all2/data/kool_lokke_channels_cache.json')
d = json.loads(p.read_text())
codes = {
  'albania':'AL','germany':'DE','italy':'IT','france':'FR','united kingdom':'UK',
  'spain':'ES','turkey':'TR','netherlands':'NL','poland':'PL','portugal':'PT',
  'romania':'RO','russia':'RU','croatia':'HR','serbia':'RS','bulgaria':'BG',
  'greece':'GR','belgium':'BE','austria':'AT','switzerland':'CH'
}
changed=0
for ch in d.values():
  if not isinstance(ch, dict): continue
  cat = (ch.get('koolCategory') or '').split('➾')[0].strip().lower()
  code = codes.get(cat, '')
  name = ch.get('name') or ''
  base = re.sub(r'\s*\[Lokke(?:,[^\]]*)?\]\s*$', '', name, flags=re.I).strip()
  tag = f'[Lokke,{code}]' if code else '[Lokke]'
  new = f'{base} {tag}'.strip()
  if new != name:
    ch['name']=new
    changed += 1
p.write_text(json.dumps(d, ensure_ascii=False, indent=2)+'\n')
print('lokke renamed', changed)
PY

echo "== Syntax check =="
node --check "$ROOT/server.js"
node --check "$ROOT/services/albService.js"
node --check "$ROOT/services/channelVariantMerge.js"
node --check "$ROOT/services/tvLogoService.js"

echo "== Restart pm2 all2 =="
pm2 restart all2 --update-env
sleep 3
pm2 list | head -20
echo DONE
