function renderHero({ chip = '', title = '', description = '', kpis = [] } = {}) {
  const kpiHtml = (kpis || []).map(item => `<div class="hero-kpi"><div class="v">${item.value}</div><div class="l">${item.label}</div></div>`).join('');
  return `<section class="main-hero" aria-label="Hero seksioni">
  <div class="main-hero-pane">
    ${chip ? `<span class="hero-chip">${chip}</span>` : ''}
    <h1>${title}</h1>
    <p>${description}</p>
  </div>
  <div class="hero-kpi-grid" aria-live="polite">${kpiHtml}</div>
</section>`;
}

function renderSearchFilters({ inputId = 'channelSearchInput', placeholder = 'Kërko...', filtersLabel = 'Filter' } = {}) {
  return `<section class="filters-shell" role="search" aria-label="Kërkim i kanaleve">
  <div class="search-bar">
    <i class="fas fa-search" aria-hidden="true"></i>
    <input id="${inputId}" type="search" placeholder="${placeholder}" autocomplete="off" aria-label="${filtersLabel}">
  </div>
  <div id="searchMeta" class="search-meta" aria-live="polite"></div>
</section>`;
}

function renderMediaGrid({ cards = '', emptyTitle = 'Nuk ka rezultate', emptyDescription = 'Provo me një term tjetër ose hiq filtrat.' } = {}) {
  const hasCards = String(cards || '').trim().length > 0;
  return `<section class="media-grid-shell">
  <div class="channels-grid" id="mediaGrid">${cards}</div>
  <div class="empty-state" id="emptyState" style="display:${hasCards ? 'none' : 'flex'}">
    <i class="fas fa-circle-info" aria-hidden="true"></i>
    <h3>${emptyTitle}</h3>
    <p>${emptyDescription}</p>
  </div>
</section>`;
}

function renderSkeletonLoaders(count = 8) {
  const qty = Math.max(1, Math.min(Number(count) || 8, 24));
  const list = Array.from({ length: qty }).map(() => `<div class="media-skeleton" aria-hidden="true">
  <div class="thumb"></div><div class="line"></div><div class="line short"></div></div>`).join('');
  return `<div class="skeleton-grid" id="skeletonGrid">${list}</div>`;
}

function renderPublicNavbar(basePath, { isHomeActive = false } = {}) {
  return `<nav aria-label="Navigimi kryesor">
<div class="nav-logo"><i class="fas fa-broadcast-tower"></i> IPTV</div>
<div class="nav-links">
<a href="${basePath}/" class="nav-link ${isHomeActive ? 'active' : ''}" aria-label="Home"><i class="fas fa-home"></i></a>
<button id="themeToggleBtn" class="nav-link" type="button" aria-label="Ndrysho temën"><i class="fas fa-moon"></i></button>
<button id="pwaInstallBtn" class="nav-link" type="button" style="display:none;background:none;border:1px solid var(--border)"><i class="fas fa-download"></i></button>
<button id="pushSubscribeBtn" class="nav-link" type="button" style="display:none;background:none;border:1px solid var(--border)"><i class="fas fa-bell"></i></button>
</div>
</nav>`;
}

module.exports = {
  renderHero,
  renderSearchFilters,
  renderMediaGrid,
  renderSkeletonLoaders,
  renderPublicNavbar
};
