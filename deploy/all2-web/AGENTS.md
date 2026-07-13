# AGENTS.md

## Cursor Cloud specific instructions

### Overview
This is a Node.js/Express IPTV streaming server that fetches live TV channels from an external API (`api.oneiptvplayer.app`) and serves them via a web UI with HLS.js player. It has a public channel browsing interface and an admin panel for channel management.

### Running the dev server
```
npm run dev
```
The server starts on `http://127.0.0.1:3000/k1/` (base path is `/k1`).

### Key endpoints
- Homepage: `/k1/`
- Categories: `/k1/category/all`, `/k1/category/sports`, `/k1/category/nimi`
- Admin login: `/k1/login` (credentials: `admin` / `kanaliim2024`)
- Admin panel: `/k1/admin`
- API ping: `POST /k1/api/ping`
- M3U playlist: `/k1/playlist`

### Gotchas
- **Startup delay**: The server calls `updateDatabases()` before listening. This fetches channels from the external API across multiple pages and takes ~10-15 seconds. The server won't respond until this completes.
- **External API dependency**: Channel data comes from `https://api.oneiptvplayer.app/api/api.php`. If this API is unreachable, the server still starts but with 0 channels. Channels refresh every 30 minutes via cron.
- **Session middleware**: The session middleware is created once. With `REDIS_URL` set, sessions use Redis (PM2 cluster safe). Without Redis, MemoryStore is used (single instance only). Do not recreate session middleware per-request.
- **PM2 cluster (optional)**: Use `ecosystem.config.cjs` in this project directory only (`pm2 start ecosystem.config.cjs`). Set `REDIS_URL=redis://127.0.0.1:6379/5` (dedicated DB index) before enabling `instances: 2`. Other PM2 apps on the VPS are not affected.
- **Redis hot cache (optional)**: When `REDIS_URL` is set, hot segment cache is shared across PM2 workers. Without Redis, in-memory cache is used per process.
- **Kool parallel catalog**: `KOOL_CATALOG_CONCURRENCY` (default 6) controls parallel category fetch in admin refresh.
- **Data directory**: Runtime state (channel configs, stats, playlists, blocked IPs) is stored in `./data/` as JSON files. This directory is auto-created on startup and is gitignored.
- **No lint/test tooling**: This project has no ESLint config or test framework. Validate changes by running the server and testing endpoints manually.
