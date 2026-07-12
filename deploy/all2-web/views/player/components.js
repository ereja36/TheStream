function renderModalHeader() {
  return `<div class="modal-header">
  <div class="modal-title" id="playerTitle">Loading...</div>
  <div style="display:flex;gap:6px;align-items:center">
    <button class="ctrl-btn" id="playerFav" onclick="toggleFavorite(currentSlug)" style="color:var(--red);font-size:.75rem" aria-label="Shto favorit"><i class="far fa-heart"></i> Favorit</button>
    <button class="modal-close" onclick="closePlayer()" aria-label="Mbyll player"><i class="fas fa-times"></i></button>
  </div>
</div>`;
}

function renderPlayerStage() {
  return `<div class="player-stage">
<video id="videoPlayer" controls autoplay preload="auto" playsinline webkit-playsinline x5-playsinline style="background:#000;min-height:200px"></video>
<div id="clapprContainer" style="display:none;background:#000;min-height:200px;width:100%"></div>
<div id="liveLogoOverlay" class="live-logo-overlay pos-top-right">
  <img id="liveLogoOverlayImg" src="" alt="Live Logo" style="display:none">
  <span id="liveLogoOverlayText" class="text" style="display:none"></span>
</div>
<div id="dynamicWatermark" class="dynamic-watermark pos-bottom-right"></div>
<div id="preRollLayer" class="preroll-layer"><div class="preroll-box"><div class="preroll-head"><span id="preRollTitle"><i class="fas fa-bullhorn" style="color:var(--orange)"></i> Sponsor</span><div style="display:flex;align-items:center;gap:8px"><button type="button" class="preroll-skip" id="preRollSkipBtn" style="display:none" onclick="skipPreRoll()">Skip</button><span id="preRollCountdown" style="color:var(--accent)">Duke filluar...</span></div></div><div class="preroll-media" id="preRollMedia"></div><div class="preroll-foot"><div id="preRollDescription">Reklamë e detyrueshme para live stream.</div><a id="preRollCta" href="#" target="_blank" rel="noopener noreferrer" class="a-btn a-btn-primary" style="display:none;margin-top:8px"></a></div></div></div>
</div>`;
}

module.exports = {
  renderModalHeader,
  renderPlayerStage
};
