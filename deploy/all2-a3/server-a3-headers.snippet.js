/**
 * A3 playback routing (live on VPS /root/all/all2/server.js).
 *
 * Fastly gate flow (from ProxyPin / Vinebre):
 *   1) GET app-measurement.fastlycache.com/.../*.js  (Content-Type: text/javascript, body = HLS)
 *      Headers: Referer/Origin aliez.tv, Cookie passport=..., X-Forwarded-For: 127.0.0.1,
 *               Icy-MetaData: 1, UA APlayer/ExoPlayer
 *   2) Playlist points to cpanel.anytools.eu.org/autologin/unique-user-id=.../1?index.php
 *   3) That .php hop is the real sessioned stream (NOT a decoy)
 *
 * App: force HLS mime for .js/.php gates + PlaylistGuard headers on both hops.
 * Web Chrome: still DC-blocked at step 1 unless residential egress exists.
 */
