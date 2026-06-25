# Publikimi në Google Play Store — Shif Shqip

## Checklist para dërgimit

### Aplikacioni
- [x] Package: `com.shif.shqip.android`
- [x] Version: 6.7.0+ (`versionCode` rritet çdo release)
- [x] Privacy Policy: https://api.shifshqip.com/privacy.php
- [x] External player: i çaktivizuar (kanalet vetëm në app)
- [ ] **Release keystore** — krijo dhe ruaj në vend të sigurt (shiko më poshtë)
- [ ] **google-services.json** me Firebase/AdMob të vërtetë
- [ ] **SERVER_KEY** dhe **REST_API_KEY** production në build (jo `XXXXX` në git)

### Play Console
1. Krijo aplikacion → **Shif Shqip**
2. Kategori: **Entertainment** ose **Video Players**
3. Content rating: plotëso kuestionarën (IPTV player, user-provided content)
4. Target audience: 18+ (rekomandohet për IPTV)
5. Data safety form:
   - Device ID: collected, për app functionality
   - Playlist URL: collected if user adds, optional
   - AdMob: advertising data
6. Store listing:
   - Titull: Shif Shqip — IPTV Player
   - Përshkrim: thekso që **nuk ofron kanale** — përdoruesi importon playlistën e vet
   - Screenshots (telefon + tablet nëse mbështetet)
   - Feature graphic 1024×500

### Keystore release
```bash
keytool -genkey -v -keystore shif-shqip-release.jks -alias shifshqip \
  -keyalg RSA -keysize 2048 -validity 10000
```
Ruaj `.jks` dhe fjalëkalimet — **nëse humbet, nuk mund të përditësosh app-in**.

Build AAB:
```bash
cd android
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

### Politika e përmbajtjes
- Mos deklaro që app-i ofron kanale TV të licencuara
- Shkruaj qartë: "Player për playlista M3U personale"
- IPTV apps shpesh kërkojnë review shtesë — përgjigju shpejt nëse Google pyet

### API & Domain
- Production API: `https://api.shifshqip.com`
- DNS: `api` A record → IP e serverit
- SSL: Let's Encrypt (certbot)

### Pas publikimit
1. Lidh app-in në AdMob me Play Store
2. Aktivizo production ad units
3. Monitoro crash reports në Play Console
