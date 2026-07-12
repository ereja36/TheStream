/**
 * A3 playback routing helpers (live on VPS /root/all/all2/server.js).
 *
 * - App + fastly: direct CDN URL (phone IP + PlaylistGuard headers)
 * - Web / other A3: CF Worker a3.phinemi.live with per-channel `rh`
 * - Gjirafa external gates: force Referer https://video.gjirafa.com/
 * - Worker rewrites gjirafa tracks-v2a1+ (audio stubs) → tracks-v1a1 (real video)
 * - preferPlayableAlbAlternate: Top Channel tch → topcha crystalweb
 */
