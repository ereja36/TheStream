# Ndërtim i APK-së pa Android Studio

Ky udhëzues shpjegon si të ndërtosh **Shif Shqip** vetëm me terminal (Gradle CLI), pa hapur Android Studio.

---

## Hapi 1 — Kërkesat

| Softuer | Versioni |
|---------|----------|
| Java JDK | 17 ose më i ri |
| Android SDK | API 36 (compileSdk) |
| Git | çdo version |

Kontrollo Java:

```bash
java -version
# duhet të shfaqë 17+
```

---

## Hapi 2 — Instalo Android SDK (Linux)

```bash
mkdir -p ~/android-sdk/cmdline-tools
cd /tmp
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-11076708_latest.zip
mv cmdline-tools ~/android-sdk/cmdline-tools/latest

export ANDROID_HOME=~/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
```

Shto në `~/.bashrc`:

```bash
export ANDROID_HOME=~/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

---

## Hapi 3 — Klonoje projektin

```bash
git clone https://github.com/ereja36/TheStream.git
cd TheStream/android
```

---

## Hapi 4 — Konfiguro SDK path

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

---

## Hapi 5 — Vendos çelësat e API

Para ndërtimit, **duhet** të konfigurosh aplikacionin:

Hap `app/src/main/java/com/shif/shqip/android/Config.java`:

```java
public static final String SERVER_KEY = "VLERA_NGA_PANELI_SETTINGS";
public static final String REST_API_KEY = "VLERA_NGA_PANELI_API_KEY";
```

### Si t'i marrësh nga paneli

1. Hyr në panel admin → **Settings**
2. **Server Key** — kopjo vlerën e enkriptuar (triple Base64)
3. **API Key** — shko te `api-key.php` → **GENERATE** → kopjo

> `SERVER_KEY` përmban URL-në e API + `_applicationId_` + `com.shif.shqip.android`

---

## Hapi 6 — (Opsionale) Firebase / Google Services

Për njoftime push dhe reklama AdMob, duhet `google-services.json` nga Firebase Console:

1. Krijo projekt Firebase me package `com.shif.shqip.android`
2. Shkarko `google-services.json`
3. Vendose në `android/app/google-services.json`

Pa këtë skedar, ndërtimi mund të dështojë për shkak të plugin-it Google Services. Opsionet:

- Shto skedarin real (jo në Git — është në `.gitignore`)
- Ose hiq plugin-in nga `build.gradle` nëse nuk përdor Firebase

---

## Hapi 7 — Ndërto APK debug

```bash
cd android
chmod +x gradlew
./gradlew assembleDebug
```

APK gjendet në:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Hapi 8 — Ndërto APK release (për Play Store)

### Krijo keystore

```bash
keytool -genkey -v -keystore shif-shqip.keystore -alias shifshqip -keyalg RSA -keysize 2048 -validity 10000
```

### Konfiguro në `app/build.gradle`

Shto brenda `android { }`:

```gradle
signingConfigs {
    release {
        storeFile file('../shif-shqip.keystore')
        storePassword 'FJALEKALIMI'
        keyAlias 'shifshqip'
        keyPassword 'FJALEKALIMI'
    }
}
buildTypes {
    release {
        signingConfig signingConfigs.release
        minifyEnabled false
    }
}
```

### Ndërto

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

---

## Hapi 9 — Instalo në pajisje

### Me USB (ADB)

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Manualisht

1. Kopjo APK në telefon
2. Aktivizo "Burime të panjohura" / "Install unknown apps"
3. Hap skedarin dhe instalo

---

## Hapi 10 — Testo pas instalimit

1. Hap aplikacionin — duhet të shfaqet ekrani i playlistës (M3U setup)
2. Fut një URL M3U ose Xtream
3. Kontrollo në panel → **Device Playlists** që pajisja u regjistrua
4. Mbyll dhe rihap aplikacionin — playlista duhet të mbetet (server-first)

---

## Ndërtim me GitHub Actions (CI)

Shembull workflow `.github/workflows/build-apk.yml`:

```yaml
name: Build APK
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - name: Setup Android SDK
        uses: android-actions/setup-android@v3
      - name: Build
        working-directory: android
        run: |
          echo "sdk.dir=$ANDROID_HOME" > local.properties
          chmod +x gradlew
          ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: android/app/build/outputs/apk/debug/app-debug.apk
```

> Duhet të vendosësh `SERVER_KEY` dhe `REST_API_KEY` si GitHub Secrets dhe t'i injektosh para build-it.

---

## Probleme të zakonshme

| Gabim | Zgjidhje |
|-------|----------|
| `SDK location not found` | Krijo `local.properties` me `sdk.dir=...` |
| `gradlew: Permission denied` | `chmod +x gradlew` |
| `App not configured` dialog | Vendos SERVER_KEY dhe REST_API_KEY në Config.java |
| `applicationId does not match` | Package në panel = `com.shif.shqip.android` |

Shiko edhe [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
