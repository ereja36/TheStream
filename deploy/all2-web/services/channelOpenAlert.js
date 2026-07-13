'use strict';

function clean(value, max = 160) {
  return String(value || '').trim().slice(0, max);
}

function buildChannelOpenAlertMessage(payload = {}) {
  const userAlias = clean(payload.userAlias || 'User', 80);
  const accountProvider = clean(payload.accountProvider || '', 60).toLowerCase();
  const accountDisplayName = clean(payload.accountDisplayName || '', 120);
  const accountEmail = clean(payload.accountEmail || '', 160);
  const channelName = clean(payload.channelName || 'Unknown Channel', 120);
  const channelSlug = clean(payload.channelSlug || '-', 120);
  const sourceLabel = clean(payload.sourceLabel || payload.sourceKey || '-', 80);
  const countryFlag = clean(payload.countryFlag || '', 8);
  const country = clean(payload.country || 'Unknown', 80);
  const countryCode = clean(payload.countryCode || '??', 8);
  const city = clean(payload.city || '-', 80);
  const ip = clean(payload.ip || '-', 80);
  const ipv4 = clean(payload.ipv4 || '-', 80);
  const ipv6 = clean(payload.ipv6 || '-', 120);
  const device = clean(payload.device || 'Unknown', 80);
  const browser = clean(payload.browser || 'Unknown', 80);
  const deviceModel = clean(payload.deviceModel || '-', 80);
  const devicePlatform = clean(payload.devicePlatform || '-', 80);
  const deviceLanguage = clean(payload.deviceLanguage || '-', 40);
  const deviceTimezone = clean(payload.deviceTimezone || '-', 60);
  const appSource = clean(payload.appSource || 'direct', 80);
  const refHost = clean(payload.refHost || '-', 120);
  const fingerprintHash = clean(payload.fingerprintHash || '-', 120);
  const fingerprintCookieId = clean(payload.fingerprintCookieId || '-', 120);
  const fingerprintIpCount = Number(payload.fingerprintIpCount || 0);
  const viewedAt = Number(payload.viewedAt || Date.now());
  const when = Number.isFinite(viewedAt) ? new Date(viewedAt).toISOString() : new Date().toISOString();

  const lines = [
    '📺 CHANNEL OPEN',
    `👤 User: ${userAlias}`,
    `Kanal: ${channelName}`,
    `Slug: ${channelSlug}`,
    `Source: ${sourceLabel}`,
    `Vendndodhja: ${countryFlag ? `${countryFlag} ` : ''}${country} (${countryCode})${city && city !== '-' ? ` / ${city}` : ''}`,
    `IP: ${ip}`,
    `IPv4: ${ipv4}`,
    `IPv6: ${ipv6}`,
    `Device: ${device} | ${browser}`,
    `Model/Platform: ${deviceModel} | ${devicePlatform}`,
    `Lang/TZ: ${deviceLanguage} | ${deviceTimezone}`,
    `Fingerprint: ${fingerprintHash}`,
    `Cookie ID: ${fingerprintCookieId}`,
    `FP IP Count: ${Number.isFinite(fingerprintIpCount) ? fingerprintIpCount : 0}`,
    `App: ${appSource}`,
    `Ref: ${refHost}`,
    `Koha: ${when}`
  ];
  if (accountProvider === 'google.com') {
    lines.splice(2, 0, `Google: ${accountDisplayName || '-'}`);
    lines.splice(3, 0, `Email: ${accountEmail || '-'}`);
  }
  return lines.join('\n');
}

module.exports = {
  buildChannelOpenAlertMessage
};
