# Android Wrapper (WebView + AdMob)

Ky projekt wrapper ngarkon:

- `https://phinemi.live/k1`

## Çfarë ka brenda

- WebView me:
  - JavaScript ON
  - DOM storage ON
  - Mixed content lejuar
  - `WebViewClient` (linket hapen brenda app-it)
  - `WebChromeClient` (JS alerts + fullscreen video)
- AdMob:
  - Banner në fund
  - Refresh banner çdo 60 sekonda
  - Interstitial preload + reload pas mbylljes
- JS bridge:
  - `AndroidInterface.showInterstitial()`
  - `AndroidInterface.setBannerVisible(boolean)`
  - `AndroidInterface.reloadBanner()`

## Kujdes

- App ID dhe Ad Unit IDs janë **test IDs**.
- Para release zëvendësoji me ID-të reale të llogarisë tënde.
- Përdorimi i wrapper-eve duhet të respektojë politikat e AdMob.

## Si ta lidhësh me formatet nga foto (AdMob)

Nga paneli yt AdMob (si në foto), krijo p.sh.:

1. **Banner**
2. **Interstitial** ose **App open**

Pastaj vendos ID-të te:

### A) App ID (AdMob app)
`android-wrapper/app/build.gradle`

```gradle
manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-xxxx~yyyy"
```

### B) Ad Unit IDs + formati fullscreen
Po te i njëjti file:

```gradle
buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-xxxx/banner_id\"")
buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-xxxx/interstitial_id\"")
buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"ca-app-pub-xxxx/app_open_id\"")
buildConfigField("String", "ADMOB_FULLSCREEN_FORMAT", "\"INTERSTITIAL\"") // ose "APP_OPEN"
buildConfigField("boolean", "ADMOB_AUTO_SHOW_APP_OPEN_ON_START", "false") // true nëse do auto në start
```

Nëse zgjedh **Interstitial** nga foto:
- lëre `ADMOB_FULLSCREEN_FORMAT = "INTERSTITIAL"`

Nëse zgjedh **App open** nga foto:
- vendos `ADMOB_FULLSCREEN_FORMAT = "APP_OPEN"`
- dhe opsionalisht `ADMOB_AUTO_SHOW_APP_OPEN_ON_START = true`

## Vendosje direkte nga Admin Page

Në panelin web ke seksionin:

- **Admin Panel -> Android AdMob (Wrapper)**

Aty vendos:

- Banner Unit ID
- Interstitial Unit ID
- App Open Unit ID
- Fullscreen format

Wrapper app i lexon automatikisht nga endpoint:

- `/k1/api/mobile-ads-config`

> Shënim: `AdMob App ID` (manifest) është vlerë build-time në Android projekt. Mund ta vendosësh edhe në admin për referencë, por në app duhet të përputhet me vlerën e build-it për release.

## JS Bridge (web -> native)

Në web mund të thërrasësh:

```js
window.AndroidAds.showFullscreenAdNative(); // përdor formatin e konfiguruar
window.AndroidAds.setFullscreenAdFormatNative("APP_OPEN"); // ose INTERSTITIAL
window.AndroidAds.showAppOpenAdNative();
window.AndroidAds.showInterstitialNative();
```
