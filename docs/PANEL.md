# Përdorimi i Panelit Admin — Shif Shqip

Ky dokument shpjegon funksionet e reja të panelit për menaxhimin e pajisjeve dhe playlistave.

---

## Hyrja në panel

- URL: `https://api.shifshqip.com/` (ose domaini yt)
- Kredencialet fillestare: `admin` / `admin`
- **Ndrysho fjalëkalimin** menjëherë pas hyrjes së parë

---

## Menuja kryesore

| Menu | Funksioni |
|------|-----------|
| Dashboard | Statistika dhe përmbledhje |
| Manage Category | Kategoritë e kanaleve (modo legacy) |
| Manage Channel | Kanalet e panelit (modo legacy) |
| **Device Playlists** | Menaxhim playlistash sipas Device ID |
| **Startup Popup** | Popup në nisje të aplikacionit |
| Manage Ads | Konfigurim reklamash globale |
| Settings | Package name, Server Key, privacy policy |
| API Key | Gjenerim REST API Key |

---

## Device Playlists

Kjo është faqja kryesore për menaxhimin e përdoruesve IPTV.

### Si funksionon

1. Përdoruesi instalon APK dhe fut playlistën (M3U URL, skedar, Xtream, portal)
2. Aplikacioni dërgon **Device ID** + playlistën te API (`register_device_playlist`)
3. Pajisja shfaqet automatikisht në këtë faqe

### Çfarë shikon në listë

| Kolona | Përshkrim |
|--------|-----------|
| Device ID | Identifikues unik i pajisjes (Android ID) |
| Playlist Name | Emri i playlistës |
| Type | `m3u`, `xtream`, `portal`, etj. |
| Source | URL ose burimi |
| Channels | Numri i kanaleve të parsuara |
| Status | Aktiv / Joaktiv |
| Blocked | Po / Jo |
| No Ads | Po / Jo |

### Veprimet

| Butoni | Veprim |
|--------|--------|
| **Ban** | Bllokon pajisjen — aplikacioni refuzon hyrjen |
| **Unban** | Heq bllokimin |
| **No Ads ON** | Çaktivizon reklamat vetëm për këtë pajisje |
| **No Ads OFF** | Riaktivizon reklamat |
| **Delete** | Fshin regjistrimin e playlistës |

### Rregullat e ban-it

- Ban-i kontrollohet në **çdo** thirrje API (`get_device_playlists`, `get_device_profile`, `register_device_playlist`)
- Pajisja e bllokuar shikon mesazhin e arsyes së bllokimit
- Unban heq regjistrimin nga `tbl_device_blocklist`

---

## Startup Popup

Konfiguron një popup që shfaqet kur aplikacioni hapet.

### Fushat

| Fushë | Përshkrim |
|-------|-----------|
| Title | Titulli i popup-it |
| Message | Teksti (për llojin `text`) |
| Popup Type | `text`, `image`, `video`, `link` |
| Media URL | URL e imazhit ose videos |
| Target URL | Link që hapet kur klikohet (për `link`) |
| Duration | Sekonda para mbylljes automatike (0 = manual) |
| Allow Manual Close | Lejo mbyllje me buton X |
| Device ID | Bosh = për të gjitha pajisjet; ose ID specifik |
| Status | Aktiv / Joaktiv |

### Shembuj

**Popup tekst për të gjithë:**
- Type: `text`
- Message: "Mirë se vini në Shif Shqip!"
- Duration: 5
- Device ID: (bosh)
- Status: Aktiv

**Popup imazh për një pajisje:**
- Type: `image`
- Media URL: `https://api.shifshqip.com/upload/banner.jpg`
- Device ID: `abc123def456`
- Status: Aktiv

---

## Settings

### Package Name

Duhet të jetë: `com.shif.shqip.android`

### Server Key

Vlera e enkriptuar që vendoset në `Config.java` → `SERVER_KEY`.

Formati i brendshëm: `https://api.shifshqip.com/the_stream_applicationId_com.shif.shqip.android`

### Privacy Policy

HTML që shfaqet në aplikacion.

---

## API Key

REST API Key përdoret nga aplikacioni Android në të gjitha thirrjet API.

1. Shko te **API Key** ose `api-key.php`
2. Kliko **GENERATE**
3. Ruaj ndryshimet
4. Kopjo vlerën në `Config.java` → `REST_API_KEY`
5. Rindërto APK

> Pas gjenerimit të ri, aplikacionet e vjetra me çelës të vjetër nuk do të funksionojnë derisa të përditësohen.

---

## Manage Ads (globale)

Konfiguron reklamat për të gjitha pajisjet që **nuk** kanë "No Ads" aktiv.

- AdMob, AppLovin, Unity, etj.
- Për pajisje specifike pa reklama, përdor **No Ads** në Device Playlists

---

## Rrjedha e plotë e një përdoruesi të ri

```
1. Instalon APK
       ↓
2. Aplikacioni merr Device ID
       ↓
3. Kontrollon ban/no-ads/popup nga API
       ↓
4. Shfaq ekranin e playlistës (nëse nuk ka playlist)
       ↓
5. Përdoruesi fut M3U/Xtream URL
       ↓
6. Aplikacioni regjistron në server (register_device_playlist)
       ↓
7. Shfaq kanalet dhe fillon shikimin
       ↓
8. Admin e sheh në panel → Device Playlists
```

---

## Këshilla për administratorët

1. **Backup** databazën para ndryshimeve të mëdha
2. **Testo ban/unban** me një pajisje test para prodhimit
3. **Mos publiko** API Key në GitHub — vetëm në APK
4. Për popup me video, përdor URL direkte (mp4), jo YouTube embed
5. Nëse faqja Device Playlists është e bardhë, ekzekuto migrimin e collation — shiko [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
