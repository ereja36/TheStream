# XUI (k1.shifshqip.com) — geo + stream control fixes

## Symptoms
- Live Connections: empty ISP, missing country flags
- Imported streams stay STOPPED a long time before UP
- Restart / Stop / Play appear to do nothing

## Root causes
1. `servers.enable_geoip` / `enable_isp` were **0**; MaxMind ISP DB was from **2021** (null for many IPs).
2. Stream start (`monitor.php`) waits for playlist readiness (many checks) — looks “stuck”.
3. Streams pulled via `http_proxy` (WARP `127.0.0.1:8118`) even for local `shifshqip.com` sources.
4. UI **Stop** often kills ffmpeg only; **monitor** restarts the stream → looks like stop failed.

## Applied on VPS
- Updated `/home/xui/bin/maxmind/GeoLite2-City.mmdb`
- Rebuilt `/home/xui/bin/maxmind/GeoIP2-ISP.mmdb` (ASN → ISP fields `isp`/`organization`)
- `UPDATE servers SET enable_geoip=1, enable_isp=1`
- Cron: `/usr/local/bin/xui-geo-backfill.php` (`/etc/cron.d/xui-geo-backfill`)
- Daemon: `/usr/local/bin/xui-signal-hook-daemon.sh` — on stop signals, also kills monitor
- Guard: `/usr/local/bin/xui-stream-guard.php` (`/etc/cron.d/xui-stream-guard`)
- Helper: `/usr/local/bin/xui-stream-ctl start|stop|restart <stream_id>`
- Removed `streams_options` proxy (`argument_id=2`) for `shifshqip.com` sources
- Trigger `signals_ai_hook` → table `signal_hooks`

## Verify
```bash
/home/xui/bin/php/bin/php /usr/local/bin/xui-geo-backfill.php
mysql xui -e "SELECT user_ip,geoip_country_code,isp FROM lines_activity ORDER BY activity_id DESC LIMIT 5;"

/usr/local/bin/xui-stream-ctl restart 121
# Live Connections should show flag + ISP within ~1 minute for new viewers
```
