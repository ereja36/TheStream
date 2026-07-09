# Lidhja e AdMob me Shif Shqip

## Çfarë të duhet

1. **Llogari Google AdMob** — https://admob.google.com
2. **Llogari Google Play Console** (për publikim)
3. **Firebase** (rekomandohet) — https://console.firebase.google.com

## Hapat

### 1. Krijo app në AdMob
- AdMob → Apps → Add App
- Platform: **Android**
- Package name: `com.shif.shqip.android`
- Emri: **Shif Shqip**
- Ruaj **App ID** (format: `ca-app-pub-XXXXXXXX~YYYYYYYY`)

### 2. Krijo Ad Units
Krijo këto njësi reklamash:
| Lloji | Përdorimi në app |
|-------|------------------|
| **App Open** | Hapja e aplikacionit |
| **Banner** | Faqet kryesore, detaj kanali |
| **Interstitial** | Para/ pas luajtjes së kanalit |
| **Native** (opsionale) | Lista kanalesh |

Kopjo **Ad Unit ID** për secilin (format: `ca-app-pub-XXXXXXXX/ZZZZZZZZ`).

### 3. Firebase + google-services.json
1. Firebase Console → Add project → **Shif Shqip**
2. Add Android app → package `com.shif.shqip.android`
3. Shkarko `google-services.json`
4. Zëvendëso file-in: `android/app/google-services.json`

### 4. Panel admin (http://api.shifshqip.com:8082 ose https://api.shifshqip.com)
Hyr te **Ads** dhe vendos:
- Ad Status: **ON**
- Ad Type: **AdMob**
- AdMob Publisher ID: `pub-XXXXXXXX` (nga AdMob → Settings)
- AdMob App ID
- Banner / Interstitial / App Open / Native Unit IDs

App-i i merr automatikisht nga API `get_config` në çdo hapje.

### 5. Manifest (tashmë i konfiguruar)
`AndroidManifest.xml` përdor `@string/admob_app_id`. Vlera default në `res/values/ads.xml` është **test ID** — paneli e mbishkruan në runtime.

### 6. Para Play Store
- Në AdMob, lidh app-in me **Google Play** (duhet APK/AAB i publikuar ose në test track)
- Aktivizo **GDPR consent** në panel (Settings → GDPR) nëse targeton BE

## Test vs Production
| | Test | Production |
|---|------|------------|
| App ID | `ca-app-pub-3940256099942544~3347511713` | Yours from AdMob |
| Banner | `.../9214589741` | Yours |

**Mos publiko në Play Store me test IDs** — Google refuzon ose suspendon.

## Premium (pa reklama)
Në panel → **Device Playlists** → Enable no-ads për `device_id`. Serveri fik reklamat via `get_config` — nuk dërgon flag `no_ads` te klienti.
