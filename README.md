# Shif Shqip — IPTV Player me Panel Admin

**Shif Shqip** është një aplikacion Android IPTV (bazuar në TheStream) i personalizuar për shqiptarët. Aplikacioni punon me playlista M3U/Xtream, menaxhohet nga një panel admin në VPS, dhe lidhet me çdo pajisje përmes **Device ID**.

| Komponent | Përshkrim |
|-----------|-----------|
| **Aplikacioni Android** | `com.shif.shqip.android` — telefon, tablet, Android TV/box |
| **Panel Admin** | PHP + MariaDB — menaxhim kanale, playlista, ban, popup, reklama |
| **API** | REST në `/api/api.php` — sinkronizim pajisje ↔ server |

---

## Veçoritë kryesore

- **M3U / Xtream / Portal URL** — fut playlist nga URL, skedar lokal, ose portal IPTV
- **Server-first** — playlista ruhet në server sipas Device ID, jo vetëm lokalisht
- **Ban / Unban** — bllokim pajisjesh nga paneli (forcohet në API)
- **No-ads për pajisje** — çaktivizo reklamat për Device ID specifik
- **Startup Popup** — imazh, video, link, kohëzgjatje, mbyllje manuale
- **EPG** — shfaq programin aktual dhe të ardhshëm në player (XMLTV)
- **VPN** — i aktivizuar (`ENABLE_VPN_ACCESS = true`)
- **Android TV** — leanback launcher, touchscreen jo i detyrueshëm

---

## Struktura e projektit

```
.
├── android/                 # Projekti Android (Gradle)
├── admin_panel/the_stream/  # Panel PHP
├── database/
│   ├── the_stream_db.sql    # Skema + të dhëna fillestare
│   └── migrations/          # Migrime shtesë
└── docs/
    ├── DEPLOYMENT.md        # Instalim VPS nga zero
    ├── ANDROID-BUILD.md     # Ndërtim APK pa Android Studio
    ├── PANEL.md             # Përdorimi i panelit
    └── TROUBLESHOOTING.md   # Zgjidhje problemesh
```

---

## Fillim i shpejtë (përmbledhje)

### 1. Kërkesat

- VPS me Ubuntu 22.04/24.04 (min. 1 GB RAM, 20 GB disk)
- Domain (p.sh. `api.shifshqip.com`) që tregon te IP e VPS
- Java 17+ në makinën ku ndërton APK (ose CI/GitHub Actions)

### 2. VPS — backend

```bash
# Instalo paketat
sudo apt update && sudo apt install -y nginx mariadb-server php8.3-fpm php8.3-mysql php8.3-curl php8.3-mbstring php8.3-xml php8.3-zip unzip git

# Krijo databazën
sudo mysql -e "CREATE DATABASE shif_shqip_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
sudo mysql -e "CREATE USER 'shif_user'@'localhost' IDENTIFIED BY 'FJALEKALIMI_YT';"
sudo mysql -e "GRANT ALL ON shif_shqip_db.* TO 'shif_user'@'localhost'; FLUSH PRIVILEGES;"

# Importo skemën
mysql -u shif_user -p shif_shqip_db < database/the_stream_db.sql
mysql -u shif_user -p shif_shqip_db < database/migrations/002_license_seed.sql
```

### 3. Panel — konfigurim

```bash
sudo mkdir -p /var/www/shifshqip-api
sudo cp -r admin_panel/the_stream /var/www/shifshqip-api/
cd /var/www/shifshqip-api/the_stream/includes
sudo cp config.example.php config.php
# Edito config.php me kredencialet e databazës
sudo nano config.php
sudo chown -R www-data:www-data /var/www/shifshqip-api
sudo chmod -R 755 /var/www/shifshqip-api
sudo chmod -R 775 /var/www/shifshqip-api/the_stream/upload
```

### 4. Nginx + SSL

Shiko udhëzimet e plota në [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

### 5. Hyrje në panel

- URL: `https://api.shifshqip.com/the_stream/` (ose domaini yt)
- **Username:** `admin`
- **Password:** `admin` (ndryshoje menjëherë pas hyrjes së parë)

### 6. Konfiguro aplikacionin

1. Hyr në panel → **Settings**
2. Vendos `package_name`: `com.shif.shqip.android`
3. Kopjo **Server Key** dhe **REST API Key**
4. Hap `android/app/src/main/java/com/shif/shqip/android/Config.java`
5. Vendos vlerat në `SERVER_KEY` dhe `REST_API_KEY`
6. Ndërto APK — shiko [docs/ANDROID-BUILD.md](docs/ANDROID-BUILD.md)

### 7. Instalo APK

```bash
adb install app-debug.apk
# ose shkarko nga serveri dhe instalo manualisht në telefon
```

---

## API endpoints (të reja për Shif Shqip)

| Endpoint | Metoda | Përshkrim |
|----------|--------|-----------|
| `register_device_playlist` | POST | Regjistron playlist për Device ID |
| `get_device_playlists` | GET | Merr playlistat aktive të pajisjes |
| `check_device_status` | GET | Kontrollon nëse pajisja është e bllokuar |
| `get_device_profile` | GET | Profili: blocked + no_ads |
| `get_startup_popup` | GET | Konfigurimi i popup-it në nisje |

Të gjitha kërkojnë `api_key` si parametër (përveç `register_device_playlist` që përdor POST body).

---

## Dokumentacion i plotë

| Dokument | Përmbajtja |
|----------|------------|
| [DEPLOYMENT.md](docs/DEPLOYMENT.md) | VPS, Nginx, SSL, MariaDB, PHP — hap pas hapi |
| [ANDROID-BUILD.md](docs/ANDROID-BUILD.md) | Ndërtim APK me Gradle CLI |
| [PANEL.md](docs/PANEL.md) | Device playlists, ban, popup, no-ads |
| [TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Gabime të zakonshme dhe zgjidhje |

---

## Bazuar në

Ky projekt është i bazuar në [TheStream](https://codecanyon.net/) (Solodroid) v6.4.1, i modifikuar dhe i rebranduar si **Shif Shqip**.

---

## Licenca

Kodi burimor i TheStream kërkon licencë CodeCanyon. Sigurohu që ke licencë të vlefshme para publikimit në Play Store.
