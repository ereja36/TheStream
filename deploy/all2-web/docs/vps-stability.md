# VPS stability harden (Shif Shqip / all2)

Applied on the live VPS to keep the server free and avoid morning OOM/false alerts.

## Changes
- Removed hourly `drop_caches` cron (was thrashing page cache)
- Cache warm every hour instead of every 20 minutes
- Cleanup every 6h without wiping PM2 logs each run
- Added 2G swap (`vm.swappiness=10`) as OOM safety net
- Scaled `all2` from 5 → 3 cluster workers (less RAM, enough for stream proxy)
- Softened php-fpm pool (`max_children` 60 → 20)
- Stopped unused Docker daemon (no containers)
- Runaway killer every 5 min: TERM/KILL non-allowlisted processes >3 GiB RSS
- Autoscale alerts: real `/proc/stat` CPU, only primary worker notifies Telegram, shared cooldown file

## Apply
```bash
scp deploy/all2-web/scripts/patch-server-autoscale-alerts.py \
    deploy/all2-web/scripts/harden-vps-stability.sh \
    root@VPS:/tmp/
ssh root@VPS 'bash /tmp/harden-vps-stability.sh'
```
