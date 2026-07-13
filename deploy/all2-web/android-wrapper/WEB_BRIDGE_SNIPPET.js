/**
 * Shtoje këtë snippet në faqen tënde web për të thirrur bridge-in Android.
 * Kjo pjesë punon vetëm kur faqja është hapur brenda WebView wrapper-it.
 */
(function () {
  function hasNative() {
    return typeof window.AndroidInterface !== "undefined";
  }

  function showInterstitialNative() {
    if (hasNative() && typeof window.AndroidInterface.showInterstitial === "function") {
      window.AndroidInterface.showInterstitial();
    }
  }

  function showFullscreenAdNative() {
    if (hasNative() && typeof window.AndroidInterface.showFullscreenAd === "function") {
      window.AndroidInterface.showFullscreenAd();
      return;
    }
    // fallback for older wrapper build
    showInterstitialNative();
  }

  function showAppOpenAdNative() {
    if (hasNative() && typeof window.AndroidInterface.showAppOpenAd === "function") {
      window.AndroidInterface.showAppOpenAd();
    }
  }

  function setFullscreenAdFormatNative(format) {
    if (hasNative() && typeof window.AndroidInterface.setFullscreenAdFormat === "function") {
      window.AndroidInterface.setFullscreenAdFormat(String(format || "INTERSTITIAL"));
    }
  }

  function setNativeBannerVisible(visible) {
    if (hasNative() && typeof window.AndroidInterface.setBannerVisible === "function") {
      window.AndroidInterface.setBannerVisible(Boolean(visible));
    }
  }

  function reloadNativeBanner() {
    if (hasNative() && typeof window.AndroidInterface.reloadBanner === "function") {
      window.AndroidInterface.reloadBanner();
    }
  }

  // Ekspozim global për ta lidhur me eventet ekzistuese të player-it:
  window.AndroidAds = {
    showFullscreenAdNative,
    showInterstitialNative,
    showAppOpenAdNative,
    setFullscreenAdFormatNative,
    setNativeBannerVisible,
    reloadNativeBanner,
  };
})();
