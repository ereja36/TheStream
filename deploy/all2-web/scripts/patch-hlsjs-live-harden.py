#!/usr/bin/env python3
"""Harden in-page HLS.js for quieter live IPTV playback.

- Pin hls.js to a stable 1.6.x release
- Larger live buffers + worker always on
- Softer media/network recover before full reload
- Slightly more patient stall recovery
"""
from __future__ import annotations

import sys
from pathlib import Path

SERVER = Path("/root/all/all2/server.js")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"FAIL {label}: expected 1 occurrence, found {count}")
    return text.replace(old, new, 1)


NEW_START_HLS = r"""function startHlsPlayback(url,attemptId){
  const video=document.getElementById('videoPlayer');
  destroyPlayers();
  currentEngine='hls';
  showEngineSurface('hls');
  bindCommonVideoEvents(video,attemptId);
  if(!(window.Hls&&Hls.isSupported&&Hls.isSupported())){
    scheduleRetry({allowFallback:true},attemptId);
    return;
  }
  // AlbKanale TV Klan path: gate→anytools MPEG-TS remuxed to HLS. Resolve relative URLs
  // and cache-bust a3hls bootstrap so each open gets a fresh remux session.
  let playUrl=String(url||'');
  try{
    if(playUrl.startsWith('/')) playUrl=new URL(playUrl, window.location.origin).toString();
  }catch{}
  const isA3Hls=/\\/a3hls(\\?|\\/|$)/i.test(playUrl);
  if(isA3Hls){
    try{
      const u=new URL(playUrl);
      if(!u.pathname.match(/\\.m3u8$/i)){
        u.searchParams.set('_', String(Date.now()));
        playUrl=u.toString();
      }
    }catch{}
    setStatus('info','Po ngarkohet (A3 HLS / AlbKanale)...');
  }
  let hlsSoftRecovers=0;
  const player=new Hls({
    // Worker keeps demux/transmux off the UI thread → fewer live freezes.
    enableWorker:true,
    lowLatencyMode:false,
    liveDurationInfinity:true,
    // Stay near live edge but absorb jitter without thrashing.
    liveSyncDurationCount:isA3Hls?3:4,
    liveMaxLatencyDurationCount:isA3Hls?12:16,
    maxLiveSyncPlaybackRate:1.15,
    backBufferLength:30,
    maxBufferLength:isA3Hls?40:90,
    maxMaxBufferLength:isA3Hls?120:180,
    maxBufferSize:60*1000*1000,
    maxBufferHole:0.5,
    highBufferWatchdogPeriod:2,
    nudgeOffset:0.1,
    nudgeMaxRetry:5,
    startFragPrefetch:true,
    testBandwidth:true,
    enableSoftwareAES:true,
    abrEwmaDefaultEstimate:500000,
    abrBandWidthFactor:0.95,
    abrBandWidthUpFactor:0.7,
    manifestLoadingMaxRetry:6,
    levelLoadingMaxRetry:6,
    fragLoadingMaxRetry:8,
    manifestLoadingRetryDelay:1000,
    levelLoadingRetryDelay:1000,
    fragLoadingRetryDelay:1000,
    manifestLoadingMaxRetryTimeout:64000,
    levelLoadingMaxRetryTimeout:64000,
    fragLoadingMaxRetryTimeout:64000,
    manifestLoadingTimeOut:isA3Hls?30000:20000,
    levelLoadingTimeOut:20000,
    fragLoadingTimeOut:isA3Hls?25000:20000
  });
  hlsPlayer=player;
  player.on(Hls.Events.ERROR,(_,data)=>{
    if(attemptId!==playbackAttempt)return;
    if(!data||!data.fatal){
      if(data&&/stall|bufferNudge|bufferStalled|bufferSeekOverHole/i.test(String(data.details||''))){
        scheduleStallRecovery(attemptId);
      }
      return;
    }
    if(data.type===Hls.ErrorTypes.MEDIA_ERROR){
      hlsSoftRecovers++;
      setStatus('warn','Po rikuperohet media...');
      try{player.recoverMediaError();}catch{}
      if(hlsSoftRecovers<=2){
        scheduleStallRecovery(attemptId);
        return;
      }
      try{player.swapAudioCodec();player.recoverMediaError();}catch{}
      scheduleRetry({allowFallback:false},attemptId);
      return;
    }
    if(data.type===Hls.ErrorTypes.NETWORK_ERROR){
      hlsSoftRecovers++;
      setStatus('warn','Rrjeti u ndërpre, po rilidhet...');
      try{player.startLoad(-1);}catch{}
      if(hlsSoftRecovers<=3){
        scheduleStallRecovery(attemptId);
        return;
      }
      if(isA3Hls){
        setStatus('warn','A3 HLS gabim, riprovë...');
        scheduleRetry({allowFallback:false},attemptId);
        return;
      }
      scheduleRetry({allowFallback:true},attemptId);
      return;
    }
    if(isA3Hls){
      setStatus('warn','A3 HLS gabim, riprovë...');
      scheduleRetry({allowFallback:false},attemptId);
      return;
    }
    scheduleRetry({allowFallback:true},attemptId);
  });
  player.on(Hls.Events.MEDIA_ATTACHED,()=>{
    if(attemptId!==playbackAttempt)return;
    try{player.loadSource(playUrl);}catch{scheduleRetry({allowFallback:true},attemptId);}
  });
  player.on(Hls.Events.MANIFEST_PARSED,()=>{
    if(attemptId!==playbackAttempt)return;
    setStatus('ok','Po luhet (HLS.js)');
    video.play().catch(()=>{});
  });
  player.on(Hls.Events.FRAG_BUFFERED,()=>{
    if(attemptId!==playbackAttempt)return;
    hlsSoftRecovers=0;
    markHealthyPlayback(attemptId);
  });
  player.attachMedia(video);
}
"""


def patch(text: str) -> str:
    text = replace_once(
        text,
        '<script src="https://cdn.jsdelivr.net/npm/hls.js@1/dist/hls.min.js"></script>',
        '<script src="https://cdn.jsdelivr.net/npm/hls.js@1.6.16/dist/hls.min.js"></script>',
        "pin-hls-js-1.6.16",
    )

    text = replace_once(
        text,
        "const MAX_RETRIES=5;\n"
        "const STALL_GRACE_MS=18000;\n"
        "const MAX_STALL_RECOVERIES=4;\n"
        "const MAX_PROXY_SOFT_RETRIES=10;",
        "const MAX_RETRIES=5;\n"
        "const STALL_GRACE_MS=22000;\n"
        "const MAX_STALL_RECOVERIES=6;\n"
        "const MAX_PROXY_SOFT_RETRIES=10;",
        "stall-patience",
    )

    start = text.index("function startHlsPlayback(url,attemptId){")
    end = text.index("function startMpegtsPlayback(url,attemptId){", start)
    old_fn = text[start:end]
    new_fn = NEW_START_HLS
    # Sanity: preserve disk escape style for isA3Hls regex
    needle = "const isA3Hls=/\\\\/a3hls(\\\\?|\\\\/|$)/i.test(playUrl);"
    if needle not in new_fn:
        raise SystemExit(f"FAIL escape style: {new_fn[new_fn.index('const isA3Hls='):new_fn.index('const isA3Hls=')+55]!r}")
    if old_fn == new_fn:
        raise SystemExit("FAIL startHlsPlayback: already patched?")
    text = text[:start] + new_fn + text[end:]
    return text


def main() -> int:
    if not SERVER.exists():
        print(f"Missing {SERVER}", file=sys.stderr)
        return 1
    original = SERVER.read_text()
    patched = patch(original)
    if "enableWorker:true" not in patched:
        raise SystemExit("FAIL: enableWorker:true missing")
    if "hls.js@1.6.16" not in patched:
        raise SystemExit("FAIL: hls pin missing")
    if "STALL_GRACE_MS=22000" not in patched:
        raise SystemExit("FAIL: stall grace missing")
    SERVER.write_text(patched)
    print(f"OK ({len(original)} -> {len(patched)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
