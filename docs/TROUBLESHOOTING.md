# Zgjidhje problemesh (Troubleshooting)

---

## Panel Admin

### Faqja Device Playlists është e bardhë

**Shkaku:** Mospërputhje collation në MariaDB (`utf8mb4_unicode_ci` vs `utf8mb4_general_ci`).

**Zgjidhja:**

```bash
mysql -u shif_user -p shif_shqip_db < database/migrations/001_collation_fix.sql
```

Ose manualisht:

```sql
ALTER TABLE tbl_device_playlist CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE tbl_device_blocklist CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE tbl_device_access CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE tbl_startup_popup CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

### API get_config kthen gabim 500

**Shkaku:** Tabela `tbl_license` është bosh.

**Zgjidhja:**

```bash
mysql -u shif_user -p shif_shqip_db < database/migrations/002_license_seed.sql
```

---

### 504 Gateway Timeout

**Shkaku:** PHP-FPM i ngadaltë ose i ndalur.

**Zgjidhja:**

```bash
systemctl restart php8.3-fpm
systemctl restart nginx
```

Kontrollo logjet:

```bash
tail -f /var/log/nginx/error.log
tail -f /var/log/php8.3-fpm.log
```

---

### Nuk mund të ngarkosh imazhe

**Shkaku:** Leje të gabuara në dosjen `upload/`.

**Zgjidhja:**

```bash
chown -R www-data:www-data /var/www/shifshqip-api/the_stream/upload
chmod -R 775 /var/www/shifshqip-api/the_stream/upload
```

---

### Gabim lidhje databaze

Kontrollo `admin_panel/the_stream/includes/config.php`:

- `$host`, `$user`, `$pass`, `$database` duhet të përputhen me MariaDB
- Testo: `mysql -u shif_user -p shif_shqip_db -e "SELECT 1;"`

---

## Aplikacioni Android

### "App not configured" në nisje

**Shkaku:** `SERVER_KEY` ose `REST_API_KEY` janë `XXXXX` në `Config.java`.

**Zgjidhja:** Vendos vlerat reale nga paneli dhe rindërto APK.

---

### "applicationId does not match"

**Shkaku:** Package name në panel nuk përputhet me APK.

**Zgjidhja:**

1. Panel → Settings → `package_name` = `com.shif.shqip.android`
2. Rigjenero Server Key nga paneli
3. Vendos në `Config.java` dhe rindërto

---

### Aplikacioni crashon në Android 16 / Pixel

**Shkaku:** Inicializim null në splash/ads/navigation.

**Zgjidhja:** Versioni aktual në këtë repo përfshin rregullime në `MyApplication.java`, `ActivitySplash.java`, `Tools.setNavigation()`. Sigurohu që ke versionin më të fundit.

---

### Playlista nuk ruhet pas restartit

**Shkaku:** API nuk është arritur ose Device ID nuk u regjistrua.

**Kontrollo:**

1. Aplikacioni ka internet
2. `SERVER_KEY` dhe `REST_API_KEY` janë të sakta
3. Në panel → Device Playlists shfaqet pajisja
4. Testo API:

```bash
curl "https://api.shifshqip.com/api/api.php?get_device_playlists&api_key=KEY&device_id=DEVICE_ID"
```

---

### Ban nuk funksionon

**Kontrollo:**

1. Në panel, statusi "Blocked" = Po
2. API përgjigjet me `blocked: true`:

```bash
curl "https://api.shifshqip.com/api/api.php?get_device_profile&api_key=KEY&device_id=DEVICE_ID"
```

3. Rihap aplikacionin (jo vetëm background) për të rifreskuar statusin

---

### Reklamat shfaqen edhe me No Ads ON

**Kontrollo:**

1. Panel → Device Playlists → No Ads = Po për atë Device ID
2. `get_device_profile` kthen `"no_ads": true`
3. Rihap aplikacionin — splash e lexon profilin në nisje

---

### EPG nuk shfaqet

**Kërkesa:** Playlista M3U duhet të ketë `url-tvg` ose `x-tvg-url` në header, plus `tvg-id` në çdo kanal.

Shembull header M3U:

```
#EXTM3U url-tvg="https://example.com/epg.xml"
#EXTINF:-1 tvg-id="channel1" tvg-name="Kanali 1",Kanali 1
http://stream.url/live
```

---

### VPN — aplikacioni nuk lidhet

Sigurohu që në `Config.java`:

```java
public static final boolean ENABLE_VPN_ACCESS = true;
```

---

### Android TV — aplikacioni nuk shfaqet në launcher

Kontrollo `AndroidManifest.xml` — duhet të ketë:

```xml
<category android:name="android.intent.category.LEANBACK_LAUNCHER" />
```

Dhe:

```xml
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
```

---

## Ndërtim APK

| Gabim | Zgjidhje |
|-------|----------|
| `SDK location not found` | Krijo `android/local.properties` |
| `gradlew: Permission denied` | `chmod +x gradlew` |
| Google Services error | Shto `google-services.json` ose hiq plugin-in |
| Out of memory | `export GRADLE_OPTS="-Xmx2048m"` |

---

## Komanda të dobishme diagnostikimi

```bash
# Statusi i shërbimeve
systemctl status nginx mariadb php8.3-fpm

# Test API
curl -s "https://api.shifshqip.com/api/api.php?get_config&package_name=com.shif.shqip.android&api_key=KEY" | jq .

# Logjet PHP
tail -50 /var/log/php8.3-fpm.log

# Logjet Nginx
tail -50 /var/log/nginx/error.log

# Pajisjet në databazë
mysql -u shif_user -p -e "SELECT device_id, playlist_name, status FROM shif_shqip_db.tbl_device_playlist;"
```

---

## Kontakt / mbështetje

Për probleme që nuk zgjidhen këtu:

1. Kontrollo logjet e mësipërme
2. Verifiko që ke versionin më të fundit nga branch `cursor/add-m3u-onboarding-3f75`
3. Hap një Issue në GitHub me: pajisje, version Android, hapat për të riprodhuar, dhe output nga logjet
