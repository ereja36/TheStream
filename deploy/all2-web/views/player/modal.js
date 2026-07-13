module.exports = `
<div class="modal-overlay" id="playerModal">
<div class="modal">
<div class="modal-header">
  <div class="modal-title" id="playerTitle">Loading...</div>
  <div style="display:flex;gap:6px;align-items:center">
    <button class="ctrl-btn" id="playerFav" onclick="toggleFavorite(currentSlug)" style="color:var(--red);font-size:.75rem"><i class="far fa-heart"></i> Favorit</button>
    <button class="modal-close" onclick="closePlayer()"><i class="fas fa-times"></i></button>
  </div>
</div>
<div class="player-stage">
<video id="videoPlayer" controls autoplay preload="auto" playsinline webkit-playsinline x5-playsinline style="background:#000;min-height:200px"></video>
<div id="clapprContainer" style="display:none;background:#000;min-height:200px;width:100%"></div>
<div id="liveLogoOverlay" class="live-logo-overlay pos-top-right">
  <img id="liveLogoOverlayImg" src="" alt="Live Logo" style="display:none">
  <span id="liveLogoOverlayText" class="text" style="display:none"></span>
</div>
<div id="liveStreamStatsOverlay" class="live-stream-stats-overlay pos-top-left">
  <div class="head">Live Stats</div>
  <div class="line" id="liveStreamStatsQuality">Quality: --</div>
  <div class="line" id="liveStreamStatsDown">Download: --</div>
  <div class="line" id="liveStreamStatsUp">Upload: --</div>
</div>
<div id="dynamicWatermark" class="dynamic-watermark pos-bottom-right"></div>
<div id="preRollLayer" class="preroll-layer">
  <div class="preroll-box">
    <div class="preroll-head">
      <span id="preRollTitle"><i class="fas fa-bullhorn" style="color:var(--orange)"></i> Sponsor</span>
      <div style="display:flex;align-items:center;gap:8px">
        <button type="button" class="preroll-skip" id="preRollSkipBtn" style="display:none" onclick="skipPreRoll()">Skip</button>
        <span id="preRollCountdown" style="color:var(--accent)">Duke filluar...</span>
      </div>
    </div>
    <div class="preroll-media" id="preRollMedia"></div>
    <div class="preroll-foot">
      <div id="preRollDescription">Reklamë e detyrueshme para live stream.</div>
      <a id="preRollCta" href="#" target="_blank" rel="noopener noreferrer" class="a-btn a-btn-primary" style="display:none;margin-top:8px"></a>
    </div>
  </div>
</div>
</div>
<div class="player-controls">
  <button class="ctrl-btn" onclick="toggleFullscreen()" title="Fullscreen"><i class="fas fa-expand"></i></button>
  <button class="ctrl-btn" onclick="togglePiP()" title="Picture-in-Picture"><i class="fas fa-external-link-alt"></i> PiP</button>
  <button class="ctrl-btn" onclick="reloadPlayer()" title="Ringarko"><i class="fas fa-redo"></i></button>
  <button class="ctrl-btn" id="shareBtn" onclick="shareCurrentChannel()" title="Shpërnda" style="display:none"><i class="fas fa-share-alt"></i></button>
  <button class="ctrl-btn" id="castBtn" onclick="castToDevice()" title="Cast" style="display:none"><i class="fas fa-tv"></i></button>
  <button class="ctrl-btn" id="airplayBtn" onclick="airplayToDevice()" title="AirPlay" style="display:none"><i class="fas fa-broadcast-tower"></i></button>
  <button class="ctrl-btn" id="notifyProgramBtn" onclick="scheduleNextProgramReminder()" title="Njofto për programin e radhës" style="display:none"><i class="fas fa-bell"></i></button>
  <button class="ctrl-btn" id="chatToggleBtn" onclick="toggleLiveChat()" title="Chat" style="display:none"><i class="fas fa-comments"></i></button>
  <div class="ctrl-url"><i class="fas fa-eye" style="margin-right:4px"></i> <span id="playerViewers"></span></div>
</div>
<div class="player-status" id="playerStatus">Po ngarkohet...</div>
<div id="playerEpgBox" style="padding:0 16px 12px;display:none">
  <div style="background:var(--card);border:1px solid var(--border);border-radius:8px;padding:8px 10px">
    <div style="font-size:.68rem;color:var(--muted);margin-bottom:4px"><i class="fas fa-tv"></i> EPG (Now / Next)</div>
    <div id="playerEpgNow" style="font-size:.74rem;color:var(--text);margin-bottom:3px"></div>
    <div id="playerEpgNext" style="font-size:.72rem;color:var(--muted)"></div>
  </div>
</div>
<div id="reactionBar" style="display:none;padding:0 16px 12px;gap:8px;flex-wrap:wrap">
  <button class="ctrl-btn" onclick="sendReaction('👍')">👍</button>
  <button class="ctrl-btn" onclick="sendReaction('❤️')">❤️</button>
  <button class="ctrl-btn" onclick="sendReaction('😂')">😂</button>
  <button class="ctrl-btn" onclick="sendReaction('😮')">😮</button>
  <button class="ctrl-btn" onclick="sendReaction('👏')">👏</button>
</div>
<div id="reactionFly" style="position:relative;height:0"></div>
<div id="liveChatPanel" style="display:none;padding:0 16px 14px">
  <div style="border:1px solid var(--border);border-radius:10px;background:var(--card)">
    <div style="display:flex;align-items:center;justify-content:space-between;padding:8px 10px;border-bottom:1px solid var(--border)">
      <b id="liveChatRoomTitle">Chat</b>
      <span style="font-size:.66rem;color:var(--muted)">Live room</span>
    </div>
    <div id="liveChatMessages" style="max-height:180px;overflow:auto;padding:6px"></div>
    <div style="display:flex;gap:6px;padding:8px;border-top:1px solid var(--border)">
      <input id="liveChatInput" class="form-input" style="margin:0;flex:1" placeholder="Shkruaj mesazh..." onkeydown="if(event.key==='Enter'){sendLiveChatMessage()}">
      <button class="a-btn a-btn-primary" onclick="sendLiveChatMessage()"><i class="fas fa-paper-plane"></i></button>
    </div>
  </div>
</div>
</div>
</div>
<div id="interstitialLayer" class="interstitial-layer">
  <div class="interstitial-box">
    <div class="interstitial-head">
      <div style="display:flex;align-items:center;gap:8px;min-width:0">
        <i class="fas fa-bullhorn" style="color:var(--orange)"></i>
        <span id="interstitialTitle" style="font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Njoftim</span>
      </div>
      <button type="button" id="interstitialCloseBtn" class="interstitial-close">Mbyll</button>
    </div>
    <div id="interstitialBody" class="interstitial-body"></div>
    <div class="interstitial-foot">
      <span id="interstitialDesc">Reklamë</span>
      <span id="interstitialCountdown"></span>
    </div>
  </div>
</div>
<div id="inlineBannerAd" class="ad-banner-slot" data-testid="inline-banner-slot"><div class="inner" id="inlineBannerInner" data-testid="inline-banner-inner"></div></div>`;
