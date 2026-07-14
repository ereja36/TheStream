#!/usr/bin/env bash
# Harden ShifShqip VPS: free RAM/CPU, stop cache thrash, stabilize alerts.
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-/root}"
ALL2_DIR="${ALL2_DIR:-/root/all/all2}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_TAG="[vps-harden]"

log() { echo "$LOG_TAG $*"; }

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    echo "Run as root" >&2
    exit 1
  fi
}

fix_crontab() {
  log "Updating root crontab (remove drop_caches, soften warm/cleanup)"
  local tmp
  tmp="$(mktemp)"
  crontab -l 2>/dev/null | grep -vE '^\s*$' | grep -v 'drop_caches' | grep -v 'warm-shifshqip-cache' | grep -v 'cleanup.sh' >"$tmp" || true
  cat >>"$tmp" <<'CRON'
# Keep page cache (do NOT drop_caches — causes RAM thrash / false pressure)
0 */6 * * * /root/cleanup.sh
0 * * * * /root/warm-shifshqip-cache.sh
*/5 * * * * /root/kill-runaway-memory.sh
CRON
  crontab "$tmp"
  rm -f "$tmp"
  crontab -l
}

install_runaway_killer() {
  log "Installing runaway memory killer"
  cat >/root/kill-runaway-memory.sh <<'EOS'
#!/usr/bin/env bash
# Kill accidental runaway processes (e.g. interactive python) before OOM kills the app.
set -euo pipefail
LIMIT_KB=$((3 * 1024 * 1024)) # 3 GiB RSS
LOG=/var/log/runaway-memory-killer.log
ALLOW_REGEX='fail2ban|unattended-upgrade|warp-svc|mysqld|mariadbd|redis-server|nginx|php-fpm|node /root/all/all2|PM2|dockerd|containerd|systemd'

ps -eo pid=,rss=,comm=,args= --no-headers | while read -r pid rss comm args; do
  [[ "$rss" =~ ^[0-9]+$ ]] || continue
  if (( rss < LIMIT_KB )); then
    continue
  fi
  line="$comm $args"
  if echo "$line" | grep -Eqi "$ALLOW_REGEX"; then
    continue
  fi
  echo "$(date -Is) killing pid=$pid rss_kb=$rss cmd=$line" >>"$LOG"
  kill -TERM "$pid" 2>/dev/null || true
  sleep 2
  if kill -0 "$pid" 2>/dev/null; then
    kill -KILL "$pid" 2>/dev/null || true
  fi
done
EOS
  chmod 755 /root/kill-runaway-memory.sh
}

soften_cleanup() {
  log "Softening cleanup.sh (no pm2 flush; keep recent logs)"
  cat >/root/cleanup.sh <<'EOS'
#!/bin/bash
set -euo pipefail
apt clean -qq 2>/dev/null || true
journalctl --vacuum-size=200M --quiet 2>/dev/null || true
find /var/log -type f \( -name '*.gz' -o -name '*.old' \) -mtime +7 -delete 2>/dev/null || true
rm -rf /tmp/chrome-* /tmp/.com.google.Chrome* /tmp/.org.chromium* 2>/dev/null || true
rm -rf /root/.cache/selenium/* /root/.wdm/* 2>/dev/null || true
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Pastrimi u krye" >> /var/log/cleanup.log
df -h / | tail -1 >> /var/log/cleanup.log
EOS
  chmod 755 /root/cleanup.sh
}

ensure_swap() {
  if swapon --show | grep -q .; then
    log "Swap already present"
    return
  fi
  if [[ -f /swapfile ]]; then
    swapon /swapfile || true
    return
  fi
  log "Creating 2G swapfile"
  fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  if ! grep -qE '^\s*/swapfile\s' /etc/fstab; then
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
  fi
  sysctl -w vm.swappiness=10 >/dev/null
  if ! grep -q '^vm.swappiness=' /etc/sysctl.conf 2>/dev/null; then
    echo 'vm.swappiness=10' >> /etc/sysctl.conf
  fi
}

soften_php_fpm() {
  local conf
  conf="$(ls /etc/php/*/fpm/pool.d/www.conf 2>/dev/null | head -1 || true)"
  [[ -n "$conf" ]] || return 0
  log "Softening php-fpm pool ($conf)"
  sed -i \
    -e 's/^pm.max_children = .*/pm.max_children = 20/' \
    -e 's/^pm.start_servers = .*/pm.start_servers = 4/' \
    -e 's/^pm.min_spare_servers = .*/pm.min_spare_servers = 2/' \
    -e 's/^pm.max_spare_servers = .*/pm.max_spare_servers = 8/' \
    "$conf"
  systemctl reload php*-fpm 2>/dev/null || systemctl reload php8.3-fpm 2>/dev/null || true
}

stop_unused_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    return 0
  fi
  local running
  running="$(docker ps -q 2>/dev/null | wc -l | tr -d ' ')"
  if [[ "$running" == "0" ]]; then
    log "No docker containers — stopping docker daemon to free RAM"
    systemctl stop docker.socket docker containerd 2>/dev/null || true
    systemctl disable docker.socket docker containerd 2>/dev/null || true
  else
    log "Docker has running containers — left alone"
  fi
}

scale_all2() {
  local instances="${PM2_INSTANCES:-3}"
  log "Scaling all2 to ${instances} cluster workers (graceful)"
  cd "$ALL2_DIR"
  # Prefer scale to avoid full outage; reload after alert patch so workers pick up new code.
  if pm2 describe all2 >/dev/null 2>&1; then
    pm2 scale all2 "$instances"
    pm2 reload all2 --update-env
  elif [[ -f ecosystem.config.cjs ]]; then
    PM2_INSTANCES="$instances" pm2 start ecosystem.config.cjs --update-env
  else
    echo "all2 process/ecosystem missing" >&2
    return 1
  fi
  pm2 save
  pm2 list
}

patch_alerts() {
  local patch="$SCRIPT_DIR/patch-server-autoscale-alerts.py"
  if [[ ! -f "$patch" ]]; then
    patch=/tmp/patch-server-autoscale-alerts.py
  fi
  if [[ ! -f "$patch" ]]; then
    log "Alert patch script missing — skip"
    return 0
  fi
  log "Patching autoscaling alerts in server.js"
  python3 "$patch" "$ALL2_DIR/server.js"
  # syntax check
  node --check "$ALL2_DIR/server.js"
}

main() {
  require_root
  fix_crontab
  install_runaway_killer
  soften_cleanup
  ensure_swap
  soften_php_fpm
  stop_unused_docker
  patch_alerts
  scale_all2
  /root/kill-runaway-memory.sh || true
  log "Done. Current memory:"
  free -h
  uptime
}

main "$@"
