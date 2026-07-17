function buildPlayerRuntimeSetup({
  playerDefaults = {},
  playerSourceDefaults = {},
  options = {},
  appConfig = {},
  normalizePlayerDefaults,
  normalizePlayerSourceDefaults,
  normalizePreRollAdConfig,
  normalizePreRollSourceOverrides,
  normalizeInterstitialAdsConfig,
  normalizeAdSnippetConfig,
  normalizeSmartLinksConfig,
  normalizeFirebaseAuthConfig,
  normalizeLiveOverlayLogoConfig,
  normalizeLiveStreamStatsConfig,
  normalizeDynamicWatermarkConfig,
  normalizeCookieConsentConfig,
  normalizePublicNotificationsConfig
}) {
  const normalizedDefaults = normalizePlayerDefaults(playerDefaults);
  const normalizedSourceDefaults = normalizePlayerSourceDefaults(playerSourceDefaults);
  const normalizedPreRollAd = normalizePreRollAdConfig(options.preRollAd !== undefined ? options.preRollAd : appConfig.pre_roll_ad);
  const normalizedPreRollOverrides = normalizePreRollSourceOverrides(options.preRollSourceOverrides !== undefined ? options.preRollSourceOverrides : appConfig.pre_roll_source_overrides);
  const normalizedInterstitialAd = normalizeInterstitialAdsConfig(options.interstitialAd !== undefined ? options.interstitialAd : appConfig.interstitial_ad);
  const normalizedAdSnippets = normalizeAdSnippetConfig(options.adSnippets !== undefined ? options.adSnippets : appConfig.ad_snippets);
  const normalizedSmartLinks = normalizeSmartLinksConfig(options.smartLinks !== undefined ? options.smartLinks : appConfig.smart_links);
  const normalizedFirebaseAuth = normalizeFirebaseAuthConfig(options.firebaseAuth !== undefined ? options.firebaseAuth : appConfig.firebase_auth);
  const normalizedLiveOverlayLogo = normalizeLiveOverlayLogoConfig(options.liveOverlayLogo !== undefined ? options.liveOverlayLogo : appConfig.live_overlay_logo);
  const normalizedLiveStreamStats = normalizeLiveStreamStatsConfig(options.liveStreamStats !== undefined ? options.liveStreamStats : appConfig.live_stream_stats);
  const normalizedDynamicWatermark = normalizeDynamicWatermarkConfig(options.dynamicWatermark !== undefined ? options.dynamicWatermark : appConfig.dynamic_watermark);
  const normalizedPublicNotifications = normalizePublicNotificationsConfig(options.publicNotifications !== undefined ? options.publicNotifications : appConfig.public_notifications);
  const normalizedCookieConsent = appConfig.disable_cookie_consent === true
    ? normalizeCookieConsentConfig({ enabled: false })
    : normalizeCookieConsentConfig(options.cookieConsent !== undefined ? options.cookieConsent : appConfig.cookie_consent);

  const pageType = String(options.pageType || 'other').trim().toLowerCase();
  const normalizedScriptOptions = {
    pageType,
    redirectKoolOnMain: !!options.redirectKoolOnMain,
    preRollAd: {
      enabled: normalizedPreRollAd.enabled && (
        !!normalizedPreRollAd.media_url
        || (normalizedPreRollAd.google_ima_enabled === true && !!normalizedPreRollAd.google_ima_vast_url)
        || (normalizedPreRollAd.ad_unit_enabled === true && (
          !!normalizedPreRollAd.ad_unit_html_popunder_best
          || !!normalizedPreRollAd.ad_unit_html_native_banner
          || !!normalizedPreRollAd.ad_unit_html_banner
        ))
      ),
      mediaType: normalizedPreRollAd.media_type,
      mediaUrl: normalizedPreRollAd.media_url,
      clickUrl: normalizedPreRollAd.click_url,
      durationSec: normalizedPreRollAd.duration_sec,
      muted: normalizedPreRollAd.muted !== false,
      title: normalizedPreRollAd.title || '',
      description: normalizedPreRollAd.description || '',
      ctaText: normalizedPreRollAd.cta_text || '',
      frequencyCapSec: Math.max(0, parseInt(normalizedPreRollAd.frequency_cap_sec, 10) || 0),
      skipEnabled: normalizedPreRollAd.skip_enabled === true,
      skipAfterSec: Math.max(0, parseInt(normalizedPreRollAd.skip_after_sec, 10) || 0),
      adUnitEnabled: normalizedPreRollAd.ad_unit_enabled === true,
      adUnitDurationSec: Math.max(1, parseInt(normalizedPreRollAd.ad_unit_duration_sec, 10) || Math.max(1, parseInt(normalizedPreRollAd.duration_sec, 10) || 5)),
      adUnitFormat: normalizedPreRollAd.ad_unit_format || 'native_banner',
      adUnitPickMode: normalizedPreRollAd.ad_unit_pick_mode === 'round_robin' ? 'round_robin' : 'random',
      adUnitPopunderBestEnabled: normalizedPreRollAd.ad_unit_popunder_best_enabled === true,
      adUnitNativeBannerEnabled: normalizedPreRollAd.ad_unit_native_banner_enabled !== false,
      adUnitBannerEnabled: normalizedPreRollAd.ad_unit_banner_enabled !== false,
      adUnitHtmlPopunderBest: normalizedPreRollAd.ad_unit_html_popunder_best || '',
      adUnitHtmlNativeBanner: normalizedPreRollAd.ad_unit_html_native_banner || '',
      adUnitHtmlBanner: normalizedPreRollAd.ad_unit_html_banner || '',
      googleImaEnabled: normalizedPreRollAd.google_ima_enabled === true,
      googleImaVastUrl: normalizedPreRollAd.google_ima_vast_url || '',
      googleImaTimeoutSec: Math.max(3, parseInt(normalizedPreRollAd.google_ima_timeout_sec, 10) || 8),
      applySources: normalizedPreRollAd.apply_sources || {},
      sourceOverrides: Object.fromEntries(Object.entries(normalizedPreRollOverrides).map(([sourceKey, cfg]) => [sourceKey, {
        enabled: cfg.enabled && !!cfg.media_url,
        mediaType: cfg.media_type,
        mediaUrl: cfg.media_url,
        clickUrl: cfg.click_url,
        durationSec: cfg.duration_sec,
        muted: cfg.muted !== false,
        title: cfg.title || '',
        description: cfg.description || '',
        ctaText: cfg.cta_text || '',
        frequencyCapSec: Math.max(0, parseInt(cfg.frequency_cap_sec, 10) || 0),
        skipEnabled: cfg.skip_enabled === true,
        skipAfterSec: Math.max(0, parseInt(cfg.skip_after_sec, 10) || 0)
      }]))
    },
    interstitialAd: {
      enabled: normalizedInterstitialAd.enabled && !!normalizedInterstitialAd.media_url,
      mediaType: normalizedInterstitialAd.media_type,
      mediaUrl: normalizedInterstitialAd.media_url,
      clickUrl: normalizedInterstitialAd.click_url,
      title: normalizedInterstitialAd.title || '',
      description: normalizedInterstitialAd.description || '',
      showOnMain: normalizedInterstitialAd.show_on_main !== false,
      showOnCategory: normalizedInterstitialAd.show_on_category !== false,
      showOnChannelOpen: normalizedInterstitialAd.show_on_channel_open === true,
      showOnPageLoad: normalizedInterstitialAd.show_on_page_load !== false,
      delaySec: Math.max(0, parseInt(normalizedInterstitialAd.delay_sec, 10) || 0),
      frequencyCapSec: Math.max(0, parseInt(normalizedInterstitialAd.frequency_cap_sec, 10) || 0),
      maxViewsPer24h: Math.max(0, parseInt(normalizedInterstitialAd.max_views_per_24h, 10) || 0),
      dismissEnabled: normalizedInterstitialAd.dismiss_enabled !== false,
      autoCloseSec: Math.max(0, parseInt(normalizedInterstitialAd.auto_close_sec, 10) || 0)
    },
    adSnippets: {
      enabled: normalizedAdSnippets.enabled === true,
      providerHint: normalizedAdSnippets.provider_hint || '',
      bannerHtml: normalizedAdSnippets.banner_html || '',
      interstitialHtml: normalizedAdSnippets.interstitial_html || '',
      bannerHtmlList: Array.isArray(normalizedAdSnippets.banner_html_list) ? normalizedAdSnippets.banner_html_list : [],
      interstitialHtmlList: Array.isArray(normalizedAdSnippets.interstitial_html_list) ? normalizedAdSnippets.interstitial_html_list : [],
      rotationMode: normalizedAdSnippets.snippet_rotation_mode === 'round_robin' ? 'round_robin' : 'random',
      ipQuotaEnabled: normalizedAdSnippets.ip_quota_enabled === true,
      ipQuotaBannerPerDay: Math.max(0, parseInt(normalizedAdSnippets.ip_quota_banner_per_day, 10) || 0),
      ipQuotaInterstitialPerDay: Math.max(0, parseInt(normalizedAdSnippets.ip_quota_interstitial_per_day, 10) || 0),
      showBannerOnMain: normalizedAdSnippets.show_banner_on_main !== false,
      showBannerOnCategory: normalizedAdSnippets.show_banner_on_category !== false,
      bannerPlacementMain: normalizedAdSnippets.banner_placement_main || 'floating_bottom',
      bannerPlacementCategory: normalizedAdSnippets.banner_placement_category || 'floating_bottom',
      bannerFrequencyCapSec: Math.max(0, parseInt(normalizedAdSnippets.banner_frequency_cap_sec, 10) || 0),
      bannerMaxViewsPer24h: Math.max(0, parseInt(normalizedAdSnippets.banner_max_views_per_24h, 10) || 0),
      bannerMultiEnabled: normalizedAdSnippets.banner_multi_enabled !== false,
      bannerMultiRows: Math.min(4, Math.max(1, parseInt(normalizedAdSnippets.banner_multi_rows, 10) || 2)),
      premiumAdsBeforeCooldown: Math.max(0, parseInt(normalizedAdSnippets.premium_ads_before_cooldown, 10) || 0),
      premiumCooldownHours: Math.max(0, parseInt(normalizedAdSnippets.premium_cooldown_hours, 10) || 0),
      premiumAfterCapAction: normalizedAdSnippets.premium_after_cap_action === 'continue_normal' ? 'continue_normal' : 'cooldown',
      premiumUserTierStorageKey: normalizedAdSnippets.premium_user_tier_storage_key || 'iptv_user_tier',
      premiumQueryParam: normalizedAdSnippets.premium_query_param || 'tier',
      adsenseEnabled: normalizedAdSnippets.adsense_enabled === true,
      adsenseClient: normalizedAdSnippets.adsense_client || '',
      adsenseSlot: normalizedAdSnippets.adsense_slot || '',
      hlsPopunderEnabled: normalizedAdSnippets.hls_popunder_enabled === true,
      hlsPopunderScriptUrl: normalizedAdSnippets.hls_popunder_script_url || '',
      hlsPopunderFrequencyCapSec: Math.max(0, parseInt(normalizedAdSnippets.hls_popunder_frequency_cap_sec, 10) || 0),
      hlsPopunderFallbackUrl: normalizedAdSnippets.hls_popunder_fallback_url || '',
      channelOpenExclusive: normalizedAdSnippets.channel_open_exclusive !== false,
      channelOpenPrimary: normalizedAdSnippets.channel_open_primary || 'auto',
      channelOpenGateEnabled: normalizedAdSnippets.channel_open_gate_enabled === true,
      channelOpenGateRequiredClicks: Math.min(3, Math.max(1, parseInt(normalizedAdSnippets.channel_open_gate_required_clicks, 10) || 1)),
      channelOpenGateStepDelaySec: Math.max(0, parseInt(normalizedAdSnippets.channel_open_gate_step_delay_sec, 10) || 0),
      channelOpenGateCooldownSec: Math.max(0, parseInt(normalizedAdSnippets.channel_open_gate_cooldown_sec, 10) || 0),
      channelOpenGateDailyLimit: Math.max(0, parseInt(normalizedAdSnippets.channel_open_gate_daily_limit, 10) || 0),
      channelOpenGateLanguage: normalizedAdSnippets.channel_open_gate_language === 'en' ? 'en' : 'sq',
      channelOpenGateTheme: normalizedAdSnippets.channel_open_gate_theme || 'classic_blue',
      channelOpenGateImageUrl: normalizedAdSnippets.channel_open_gate_image_url || '',
      channelOpenGateTitleSq: normalizedAdSnippets.channel_open_gate_title_sq || '',
      channelOpenGateTitleEn: normalizedAdSnippets.channel_open_gate_title_en || '',
      channelOpenGateSubtitleSq: normalizedAdSnippets.channel_open_gate_subtitle_sq || '',
      channelOpenGateSubtitleEn: normalizedAdSnippets.channel_open_gate_subtitle_en || '',
      channelOpenGateFooterSq: normalizedAdSnippets.channel_open_gate_footer_sq || '',
      channelOpenGateFooterEn: normalizedAdSnippets.channel_open_gate_footer_en || '',
      channelOpenGateOpenButtonSq: normalizedAdSnippets.channel_open_gate_open_button_sq || '',
      channelOpenGateOpenButtonEn: normalizedAdSnippets.channel_open_gate_open_button_en || '',
      channelOpenGateCancelButtonSq: normalizedAdSnippets.channel_open_gate_cancel_button_sq || '',
      channelOpenGateCancelButtonEn: normalizedAdSnippets.channel_open_gate_cancel_button_en || '',
      channelOpenGateUseSmartlink: normalizedAdSnippets.channel_open_gate_use_smartlink !== false,
      channelOpenGateUseNative: normalizedAdSnippets.channel_open_gate_use_native !== false,
      channelOpenGateUseBanner: normalizedAdSnippets.channel_open_gate_use_banner !== false,
      channelOpenGateClickMode: normalizedAdSnippets.channel_open_gate_click_mode === 'iframe' ? 'iframe' : 'smartlink',
      channelOpenGateShowCancel: normalizedAdSnippets.channel_open_gate_show_cancel === true,
      channelOpenGateAutoOpenSec: Math.max(0, parseInt(normalizedAdSnippets.channel_open_gate_auto_open_sec, 10) || 0),
      channelOpenGateSmartlinkUrl: normalizedAdSnippets.channel_open_gate_smartlink_url || '',
      channelOpenGateNativeUrl: normalizedAdSnippets.channel_open_gate_native_url || '',
      channelOpenGateBannerUrl: normalizedAdSnippets.channel_open_gate_banner_url || '',
      channelOpenGateNativeScript: normalizedAdSnippets.channel_open_gate_native_script || '',
      channelOpenGateBannerScript: normalizedAdSnippets.channel_open_gate_banner_script || '',
      channelOpenGateApplySources: normalizedAdSnippets.channel_open_gate_apply_sources || {}
    },
    smartLinks: {
      enabled: normalizedSmartLinks.enabled === true,
      openInNewTab: normalizedSmartLinks.open_in_new_tab !== false,
      onChannelOpen: normalizedSmartLinks.on_channel_open !== false,
      onCategoryOpen: normalizedSmartLinks.on_category_open !== false
    },
    firebaseAuth: {
      enabled: normalizedFirebaseAuth.enabled === true,
      apiKey: normalizedFirebaseAuth.api_key || '',
      authDomain: normalizedFirebaseAuth.auth_domain || '',
      projectId: normalizedFirebaseAuth.project_id || '',
      storageBucket: normalizedFirebaseAuth.storage_bucket || '',
      appId: normalizedFirebaseAuth.app_id || '',
      messagingSenderId: normalizedFirebaseAuth.messaging_sender_id || '',
      measurementId: normalizedFirebaseAuth.measurement_id || '',
      maxDevicesEnabled: normalizedFirebaseAuth.max_devices_enabled === true,
      maxDevices: Math.max(1, parseInt(normalizedFirebaseAuth.max_devices, 10) || 2),
      trialEnabled: normalizedFirebaseAuth.trial_enabled === true,
      trialDays: Math.max(1, parseInt(normalizedFirebaseAuth.trial_days, 10) || 3),
      trialMaxUses: Math.max(1, parseInt(normalizedFirebaseAuth.trial_max_uses, 10) || 1),
      referralRewardDays: Math.max(1, parseInt(normalizedFirebaseAuth.referral_reward_days, 10) || 3),
      referralReferredBonusDays: Math.max(0, parseInt(normalizedFirebaseAuth.referral_referred_bonus_days, 10) || 0),
      requireGoogleLogin: normalizedFirebaseAuth.require_google_login === true,
      freeChannelSlugs: Array.isArray(normalizedFirebaseAuth.free_channel_slugs) ? normalizedFirebaseAuth.free_channel_slugs : []
    },
    liveOverlayLogo: {
      enabled: normalizedLiveOverlayLogo.enabled && (!!normalizedLiveOverlayLogo.logo_url || !!normalizedLiveOverlayLogo.text),
      logoUrl: normalizedLiveOverlayLogo.logo_url,
      text: normalizedLiveOverlayLogo.text || '',
      position: normalizedLiveOverlayLogo.position || 'top-right',
      opacity: Math.min(Math.max(parseFloat(normalizedLiveOverlayLogo.opacity) || 0.85, 0.1), 1),
      widthPx: Math.min(Math.max(parseInt(normalizedLiveOverlayLogo.width_px, 10) || 120, 40), 420),
      applySources: normalizedLiveOverlayLogo.apply_sources || {}
    },
    liveStreamStats: {
      enabled: normalizedLiveStreamStats.enabled === true,
      position: normalizedLiveStreamStats.position || 'top-left',
      fontSizePx: Math.min(Math.max(parseInt(normalizedLiveStreamStats.font_size_px, 10) || 12, 11), 24),
      maxWidthPx: Math.min(Math.max(parseInt(normalizedLiveStreamStats.max_width_px, 10) || 220, 120), 420),
      opacity: Math.min(Math.max(parseFloat(normalizedLiveStreamStats.opacity) || 0.48, 0.12), 1),
      refreshSec: Math.min(Math.max(parseInt(normalizedLiveStreamStats.refresh_sec, 10) || 14, 4), 60),
      showQuality: normalizedLiveStreamStats.show_quality !== false,
      showDownload: normalizedLiveStreamStats.show_download !== false,
      showUpload: normalizedLiveStreamStats.show_upload !== false,
      applySources: normalizedLiveStreamStats.apply_sources || {}
    },
    dynamicWatermark: {
      enabled: normalizedDynamicWatermark.enabled === true,
      position: normalizedDynamicWatermark.position || 'bottom-right',
      opacity: Math.min(Math.max(parseFloat(normalizedDynamicWatermark.opacity) || 0.28, 0.12), 1),
      showIpSuffix: normalizedDynamicWatermark.show_ip_suffix !== false,
      updateSec: Math.min(Math.max(parseInt(normalizedDynamicWatermark.update_sec, 10) || 8, 3), 30),
      applySources: normalizedDynamicWatermark.apply_sources || {}
    },
    publicNotifications: {
      enabled: normalizedPublicNotifications.enabled === true,
      liveEnabled: normalizedPublicNotifications.live_enabled === true,
      partnershipEnabled: normalizedPublicNotifications.partnership_enabled === true,
      adsEnabled: normalizedPublicNotifications.ads_enabled === true,
      liveTitle: normalizedPublicNotifications.live_title || 'Ndeshje Live',
      liveText: normalizedPublicNotifications.live_text || '',
      liveUrl: normalizedPublicNotifications.live_url || '',
      partnershipTitle: normalizedPublicNotifications.partnership_title || 'Partnership',
      partnershipText: normalizedPublicNotifications.partnership_text || '',
      partnershipUrl: normalizedPublicNotifications.partnership_url || '',
      adsTitle: normalizedPublicNotifications.ads_title || 'Sponsored',
      adsText: normalizedPublicNotifications.ads_text || '',
      adsUrl: normalizedPublicNotifications.ads_url || ''
    },
    cookieConsent: {
      enabled: normalizedCookieConsent.enabled !== false,
      title: normalizedCookieConsent.title || 'Cookie Settings',
      description: normalizedCookieConsent.description || '',
      policyUrl: normalizedCookieConsent.policy_url || '',
      allowDeviceDetailsDefault: normalizedCookieConsent.allow_device_details_default === true,
      allowBatteryDefault: normalizedCookieConsent.allow_battery_default === true,
      allowContactField: normalizedCookieConsent.allow_contact_field !== false,
      allowAnalyticsDefault: normalizedCookieConsent.allow_analytics_default === true,
      allowAdsPersonalizationDefault: normalizedCookieConsent.allow_ads_personalization_default === true,
      forceChoiceOnSports: normalizedCookieConsent.force_choice_on_sports === true
    },
    security: {
      hideHiddenCategoryHints: true
    }
  };

  return {
    normalizedDefaults,
    normalizedSourceDefaults,
    normalizedScriptOptions
  };
}

module.exports = {
  buildPlayerRuntimeSetup
};
