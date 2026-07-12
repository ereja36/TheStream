/**
 * A3 playback routing helpers (live on VPS /root/all/all2/server.js).
 *
 * Config.php notes (webviews vs channels):
 * - webviews (main/crazy/chat/pay) are cloaking/tools pages — NOT required for stream auth.
 * - Playback needs per-channel: url + headers + custom_user_agent from channels[].
 * - Strip loopback X-Forwarded-For: 127.0.0.1. cip/signature track the config TCP peer.
 *
 * Routing:
 * - App + fastly: direct CDN URL (phone residential IP + aliez Referer/Origin/Cookie)
 * - Safari/iOS web + fastly: direct_url via native HLS (viewer IP, no CORS)
 * - Chrome web + fastly: blocked (Worker/VPS DC IP → 404/502); show a3_fastly_web hint
 * - Other A3 (gcore/gjirafa): CF Worker a3.phinemi.live with per-channel `rh`
 * - Worker rewrites gjirafa tracks-v2a1+ (audio stubs) → tracks-v1a1 (real video)
 * - preferPlayableAlbAlternate: Top Channel tch → topcha crystalweb
 */
