  }
  return { groupTitle, groupLogo };
}
function renderCategoryIconHtml(logoUrl = '', iconClass = 'fa-folder-open') {
  const safeLogo = normalizeHttpUrl(logoUrl);
  const safeIcon = String(iconClass || 'fa-folder-open').trim() || 'fa-folder-open';
  if (safeLogo) {
    return `<div class="cat-icon cat-icon-image"><img src="${escapeHtmlAttr(safeLogo)}" alt="" loading="lazy"></div>`;
  }
  return `<div class="cat-icon"><i class="fas ${escapeHtmlAttr(safeIcon)}"></i></div>`;
}

function buildNativeAdSlotHtml(adSettings, index = 0) {
  const nativeSnippets = [String(adSettings.banner_html || '').trim(), ...(Array.isArray(adSettings.banner_html_list) ? adSettings.banner_html_list : [])]
    .map(v => String(v || '').trim())
    .filter(Boolean);
  if (!nativeSnippets.length) return '';
  const pick = nativeSnippets[index % nativeSnippets.length];
  const encoded = Buffer.from(String(pick || ''), 'utf8').toString('base64');
  return `<div class="category-ad-slot native-ad-card" data-native-ad="1"><div class="native-ad-head"><i class="fas fa-bullhorn"></i> Sponsored</div><div class="native-ad-body" data-ad-snippet="${encoded}"></div></div>`;
}
function matchTriangoloAutoGroup(name = '', sourceKey = '') {
  const n = String(name || '').toLowerCase();
  const src = String(sourceKey || '').toLowerCase();
  if (src === 'api_sport' || (src === 'api' && /sport/.test(n)) || /\b(sport|dazn|bein|arena|eurosport|super sport|art sport)\b/.test(n)) return 'sport';
  if (/\b(film|movie|cinema|hbo|netflix|cinemax|axn)\b/.test(n)) return 'filma';
  if (/\b(kids|femij|cartoon|disney|nick|moonbug|zoomoo|baby|junior)\b/.test(n)) return 'femije';
  if (/\b(music|muzik|mtv|kiss|radio|deluxe)\b/.test(n)) return 'muzike';
  if (/\b(news|lajm|cnn|bbc|al jazeera|france 24|sky news)\b/.test(n)) return 'lajme';
  if (/\b(top channel|top news|abc|syri|a2|euronews|rtsh|klan|vizion|report tv|news 24|alsat|oranews|tv 21|dukagjini|rtk|koha)\b/.test(n)) return 'kombetare';
  return '';
}
function buildTriangoloCategoryRowsHtml(req = null) {
  const adSettings = normalizeAdSnippetConfig(appConfig.ad_snippets);
  const enableAds = adSettings.enabled === true && adSettings.grid_native_enabled === true;
  const visible = filterChannelsForDevice(visibleChannels(allCombined), req);
  const rows = [];
  let adIndex = 0;
  const used = new Set();

  const pushRow = (id, title, channelEntries, viewAllHref, kicker = 'TV CHANNELS') => {
    const limited = channelEntries.filter(([slug]) => !!visible[slug] && !used.has(slug)).slice(0, 18);
    if (!limited.length) return;
    limited.forEach(([slug]) => used.add(slug));
    const cards = buildCards(Object.fromEntries(limited), {
      pageType: 'triangolo_row',
      categoryKeyOverride: `custom:${createSlug(id)}`,
      redirectKoolOnMain: false
    });
    rows.push(`<section class="tri-cat-row" data-cat="${escapeHtmlAttr(id)}">
  <div class="tri-cat-head">
    <div>
      <div class="tri-cat-kicker">${escapeHtml(kicker)}</div>
      <h2 class="tri-cat-title">${escapeHtml(title)}</h2>
    </div>
    <a class="tri-view-all" href="${viewAllHref}">View all</a>
  </div>
  <div class="tri-cat-scroller channels-grid tri-h-scroll">${cards}</div>
  ${enableAds ? buildNativeAdSlotHtml(adSettings, adIndex++) : ''}
</section>`);
  };

  const entries = Object.entries(customCategories || {})
    .filter(([, cat]) => cat?.showOnMain !== false)
    .sort((a, b) => (Number(a[1].order || 0) - Number(b[1].order || 0)) || String(a[1].name || '').localeCompare(String(b[1].name || '')));
  for (const [id, cat] of entries) {
    const channels = getCustomCategoryChannels(id);
    const channelEntries = Object.entries(channels);
    pushRow(id, cat.name || id, channelEntries, `${BASE_PATH}/category/custom/${encodeURIComponent(id)}`);
  }

  // Auto rows when custom categories are empty / sparse (Triangolo homepage like AlbKanale)
  if (rows.length < 3) {
    const buckets = {
      kombetare: [],
      sport: [],
      filma: [],
      lajme: [],
      femije: [],
      muzike: [],
      lokale: []
    };
    const labels = {
      kombetare: 'Kombetare',
      sport: 'Sport',
      filma: 'Filma',
      lajme: 'Lajme',
      femije: 'Familje',
      muzike: 'Muzike',
      lokale: 'Lokale'
    };
    const order = ['kombetare', 'sport', 'filma', 'lajme', 'femije', 'muzike', 'lokale'];
    for (const [slug, ch] of Object.entries(visible)) {
      if (used.has(slug)) continue;
      const src = getChannelSourceKey(ch);
      const isAlbLokke = src === 'kool_lokke' && /albania/i.test(String(ch.koolCategory || ch.description || ch.name || ''));
      const isApi = src === 'api' || src === 'api_tv' || src === 'api_sport' || ch.source === 'api';
      if (!isAlbLokke && !isApi && src !== 'custom_tv') continue;
      let key = matchTriangoloAutoGroup(ch.name || slug, src);
      if (!key) {
        if (src === 'api_sport' || ch.type === 'sport') key = 'sport';
        else if (isAlbLokke || isApi) key = 'lokale';
        else continue;
      }
      if (!buckets[key]) key = 'lokale';
      buckets[key].push([slug, ch]);
    }
    for (const key of order) {
      const list = buckets[key] || [];
      if (list.length < 2) continue;
      const viewAll = customCategories[key]
        ? `${BASE_PATH}/category/custom/${encodeURIComponent(key)}`
        : `${BASE_PATH}/`;
      pushRow(`auto-${key}`, labels[key] || key, list, viewAll);
      if (rows.length >= 8) break;
    }
  }

  if (!rows.length) {
    const featuredSlugs = Array.isArray(appConfig.featured_channels) ? appConfig.featured_channels.filter(slug => !!visible[slug]) : [];
    const limited = (featuredSlugs.length ? featuredSlugs.map(s => [s, visible[s]]) : Object.entries(visible)).slice(0, Math.max(12, Number(appConfig.main_page_limit) || 20));
    pushRow('live', 'Live', limited.filter(([, ch]) => !!ch), `${BASE_PATH}/`);
  }
  return rows.join('\n');
}

function buildCustomCategoryCardsHtml() {
  const visibleCustomCategoryEntries = Object.entries(customCategories)
    .filter(([, cat]) => cat?.showOnMain !== false)
    .sort((a, b) => (Number(a[1].order || 0) - Number(b[1].order || 0)) || String(a[1].name || '').localeCompare(String(b[1].name || '')));
  return visibleCustomCategoryEntries.map(([id, cat]) => {
    const count = Object.keys(getCustomCategoryChannels(id)).length;
    const iconHtml = renderCategoryIconHtml(cat.logo, 'fa-folder-open');
    return `<a href="${BASE_PATH}/category/custom/${id}" class="cat-card" onclick="return openCategoryWithSmartLink(event,'custom:${id}')">
${iconHtml}
<div class="cat-info"><h3>${escapeHtml(cat.name || id)}</h3><p>Kategori e personalizuar</p></div>
<div class="cat-count">${count}</div></a>`;
  }).join('');
}
function buildKoolLokkeCategoryCardsHtml() {
  return getKoolLokkeCategorySummaries()
    .filter(item => item.visibleOnMain)
    .map(item => `<a href="${BASE_PATH}/category/kool-lokke/${encodeURIComponent(item.slug)}" class="cat-card" onclick="return openCategoryWithSmartLink(event,'kool_lokke:${item.slug}')">
<div class="cat-icon"><i class="fas fa-satellite"></i></div>
<div class="cat-info"><h3>${escapeHtml(item.name)}</h3><p>LokkeApp</p></div>
<div class="cat-count">${item.count}</div></a>`)
    .join('');
}
