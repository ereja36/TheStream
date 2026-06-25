# AGENTS.md

## Cursor Cloud specific instructions

### What this repo is
This repo ships the CodeCanyon product **"The Stream – TV & Video Streaming App"** as zip archives at the
repo root (`codecanyon-*-the-stream-tv-video-streaming-app.zip`; the two zips are byte-identical). There is
no source checked into git — the source lives inside the zips. The product has two parts:

- **Admin Panel + REST API** — plain PHP (procedural) + MySQL/MariaDB. Serves the admin dashboard and the
  JSON REST API (`/api/api.php`) the mobile app consumes. This is the runnable backend.
- **Android app** — native Java client (Gradle), in `app/the_stream_6.4.1/android_studio/TheStream/`.
  It is a build-only client (needs Android SDK + `google-services.json`); not run in this environment.

The update script extracts both archives into the git-ignored `app/` directory:
`app/the_stream_6.4.1/admin_panel/the_stream_panel/the_stream/` is the PHP web root (referred to below as
`$PANEL`).

### Services and how to run them
The MariaDB data dir and the seeded `the_stream_db` database persist in the VM snapshot, so normally you only
need to (re)start the two services below.

1. **MariaDB** (port 3306, unix socket `/run/mysqld/mysqld.sock`). Start it if not running:
   ```bash
   sudo mkdir -p /var/run/mysqld && sudo chown mysql:mysql /var/run/mysqld
   (sudo mariadbd-safe >/tmp/mariadb.log 2>&1 &)
   ```
2. **PHP dev server** (admin panel + REST API, port 8080). **Must run as root** because `includes/config.php`
   connects as MySQL user `root` with an empty password over the unix socket (`root` uses `unix_socket`
   auth, so the OS process must be root):
   ```bash
   PANEL="$(ls -d /workspace/app/the_stream_6.4.1/admin_panel/the_stream_panel/the_stream)"
   sudo php -S 0.0.0.0:8080 -t "$PANEL"
   ```

### Non-obvious gotchas
- **PHP mysqli socket path**: `/var/run` is NOT symlinked to `/run` in this VM, so PHP's default
  `mysqli.default_socket` (`/var/run/mysqld/mysqld.sock`) does not match MariaDB's actual socket
  (`/run/mysqld/mysqld.sock`). This is fixed by `/etc/php/8.3/cli/conf.d/99-mariadb-socket.ini` (persisted in
  the snapshot). Without it, every DB connection fails with "No such file or directory".
- **License gate**: every admin page redirects to `verify.php` unless `tbl_license.item_id = 19956555`, and
  the REST API's `validateResourceAccess` requires `tbl_license.purchase_code` to be **36 or 44 chars long**.
  The seeded DB has a local-dev license row satisfying both (`purchase_code` is a 36-char placeholder). Do not
  blank it out or the API endpoints (e.g. `get_posts`) return empty bodies.
- **`error_reporting(0)`** is set throughout the app, so failures show as blank pages / empty API responses.
  When debugging, run `php -d display_errors=1 -d error_reporting=E_ALL ...` or check `/tmp/php-server.log`.
- The Android app is not built/run here; it only consumes the backend (`Config.SERVER_KEY` = base64 backend
  URL, `Config.REST_API_KEY` = the panel `api_key`).

### Credentials & keys (local dev only)
- Admin panel login (at `/index.php`): username `admin`, password `admin`
  (stored as `sha256(username . password)`).
- DB: user `root`, empty password, schema `the_stream_db`.
- REST `api_key` (from `tbl_settings`): `cda11bx8aITlKsXCpNB7yVLnOdEGqg342ZFrQzJRetkSoUMi9w`.

### Recreating the database (only if the snapshot DB is missing)
```bash
SQL="$(ls /workspace/app/the_stream_6.4.1/admin_panel/the_stream_db.sql)"
sudo mariadb -e "CREATE DATABASE IF NOT EXISTS the_stream_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
sudo mariadb the_stream_db < "$SQL"
sudo mariadb the_stream_db -e "INSERT INTO tbl_license (id,purchase_code,item_id,item_name,buyer,license_type,purchase_date) VALUES (1,'12345678-1234-1234-1234-123456789012',19956555,'The Stream','localdev','Regular','2025-01-01') ON DUPLICATE KEY UPDATE purchase_code=VALUES(purchase_code), item_id=VALUES(item_id);"
```

### Quick smoke tests
```bash
curl -s "http://localhost:8080/api/api.php?check_connection"
curl -s "http://localhost:8080/api/api.php?get_config&package_name=com.app.thestream&api_key=cda11bx8aITlKsXCpNB7yVLnOdEGqg342ZFrQzJRetkSoUMi9w"
```
