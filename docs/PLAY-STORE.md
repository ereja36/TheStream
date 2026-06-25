# Publikimi në Google Play Store — Shif Shqip

## Checklist para dërgimit

### Aplikacioni
- [x] Package: `com.shif.shqip.android`
- [x] Version: 6.9.0 (`versionCode` 31)
- [x] Privacy Policy: https://shifshqip.com/privacy
- [x] API panel: https://api.shifshqip.com (vetëm admin/API)
- [x] Release keystore: `android/keystore/shif-shqip-release.jks`
- [x] `app-ads.txt`: https://api.shifshqip.com/app-ads.txt dhe https://shifshqip.com/app-ads.txt
- [x] SERVER_KEY dhe REST_API_KEY production në `Config.java`

### Çfarë të bësh me APK / AAB

#### 1. Build release (lokal ose CI)
```bash
cd android
./gradlew assembleRelease bundleRelease
```

Skedarët:
- **APK** (sideload / test): `android/app/build/outputs/apk/release/app-release.apk`
- **AAB** (Play Store): `android/app/build/outputs/bundle/release/app-release.aab`

> Për Play Store përdor **vetëm AAB**, jo APK.

#### 2. Play Console — krijo aplikacionin
1. Hyr në [Google Play Console](https://play.google.com/console)
2. **Create app** → Shif Shqip
3. Kategori: **Entertainment** ose **Video Players**
4. Content rating: plotëso kuestionarën (IPTV, user-provided content)
5. Target audience: 18+ (rekomandohet)

#### 3. Store listing
- Titull: **Shif Shqip — IPTV Player**
- Përshkrim: thekso që app-i **nuk ofron kanale** — përdoruesi importon playlistën e vet (M3U/Xtream)
- Screenshots telefoni + tablet
- Feature graphic **1024×500**
- Icon **512×512** (logo e kuqe ShifShqip)
- Privacy policy URL: `https://shifshqip.com/privacy`

#### 4. Ngarko AAB
1. **Release** → **Production** (ose Internal testing së pari)
2. **Create new release**
3. Upload `app-release.aab`
4. Release notes (shqip/anglisht)
5. Submit for review

#### 5. Data safety
- Device ID: collected, app functionality
- Playlist URL: optional, user-provided
- AdMob: advertising data (nëse ads janë ON)

#### 6. Signing
Play App Signing: rekomandohet **Google-managed**. Keystore lokal përdoret për upload key.

---

## AdMob & Google Console

### AdMob
1. [AdMob Console](https://apps.admob.com/) → Add app → Android → `com.shif.shqip.android`
2. Lidh me Play Store pas publikimit (ose manual për test)
3. Krijo ad units: Banner, Interstitial, App Open (si në panel)
4. Verifiko **Publisher ID** në `android/app/src/main/res/values/ads.xml` përputhet me AdMob
5. `app-ads.txt` duhet të ketë:
   ```
   google.com, pub-XXXXXXXX, DIRECT, f08c47fec0942fa0
   ```
6. Në Play Console → **App content** → link domain `shifshqip.com` për ads.txt

### Firebase (opsionale)
Nëse përdor FCM push (jo inbox notifications):
- `google-services.json` në `android/app/`
- Panel → Settings → Firebase provider

**In-app notifications** (pa Firebase): Panel → **In-App Notifications**

---

## Domainet

| URL | Qëllimi |
|-----|---------|
| `https://api.shifshqip.com` | Panel admin + REST API |
| `https://shifshqip.com` | Website, privacy, marketing |
| `https://api.shifshqip.com/shif-shqip-latest.apk` | APK sideload (jo për Play Store) |

`privacy.php` në API redirecton te `https://shifshqip.com/privacy`.

---

## Pas publikimit
1. Lidh app-in në AdMob me Play listing
2. Aktivizo production ad units në panel
3. Monitoro crashes në Play Console
4. Përditëso `versionCode` çdo release

---

## Politika e përmbajtjes
- Mos deklaro që app-i ofron kanale TV të licencuara
- Shkruaj: "Player për playlista M3U/Xtream personale"
- IPTV apps mund të kërkojnë review shtesë — përgjigju shpejt nëse Google pyet
