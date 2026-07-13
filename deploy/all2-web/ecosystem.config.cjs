'use strict';

// PM2 config ONLY for the IPTV app. Does not touch other PM2 processes on the VPS.
// Usage on VPS (from project directory):
//   pm2 delete all2 2>/dev/null; pm2 start ecosystem.config.cjs
//   pm2 save

const appName = process.env.PM2_APP_NAME || 'all2';
const instancesRaw = parseInt(process.env.PM2_INSTANCES, 10);
const instances = Number.isFinite(instancesRaw) ? Math.min(Math.max(instancesRaw, 1), 6) : 2;

module.exports = {
  apps: [
    {
      name: appName,
      script: 'server.js',
      cwd: __dirname,
      instances,
      exec_mode: instances > 1 ? 'cluster' : 'fork',
      listen_timeout: 120000,
      kill_timeout: 15000,
      max_memory_restart: '1500M',
      env: {
        NODE_ENV: 'production',
        // Use a dedicated Redis DB index so other projects stay isolated.
        REDIS_URL: process.env.REDIS_URL || 'redis://127.0.0.1:6379/5',
        REDIS_KEY_PREFIX: process.env.REDIS_KEY_PREFIX || 'k1iptv:',
        KOOL_CATALOG_CONCURRENCY: process.env.KOOL_CATALOG_CONCURRENCY || '6',
        KOOL_CATALOG_MAX_PAGES: process.env.KOOL_CATALOG_MAX_PAGES || '20',
        PM2_INSTANCES: String(instances)
      }
    }
  ]
};
