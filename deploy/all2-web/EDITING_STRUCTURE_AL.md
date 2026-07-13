# Strukturë më e lehtë për editim

Ky projekt tani ka ndarje më të qartë për pjesët kryesore:

- **Main page public**
  - `views/public/mainPage.js`
  - `views/public/mainPage.ejs`
- **Faqet e kategorive**
  - `views/public/categoryPage.js`
  - `views/public/categoryPage.ejs`
- **Layout i faqeve public**
  - `views/public/layout.js`
  - `views/public/layout.ejs`
- **Player modal (UI i player-it)**
  - `views/player/modal.js`
- **Player runtime config (opsionet e script-it)**
  - `views/player/runtimeConfig.js`
- **Seksioni Android AdMob në Admin**
  - `views/admin/androidAdmobSection.js`
- `views/admin/androidAdmobSection.ejs`
- **Faqja e statistikave (Admin)**
  - `views/admin/statsPage.js`
  - `views/admin/statsPage.ejs`
- **Template helper**
  - `views/renderTemplate.js` (EJS renderer + cache)

Këto file përdoren nga `server.js`, kështu që mund të editosh UI pa prekur logjikën core.

## Sugjerime për përmirësim (hapat e radhës)

1. **Nxirr edhe `/admin` (dashboard kryesor) në renderer të veçantë**
   - p.sh. `views/admin/adminPage.js`
   - server.js të mbajë vetëm logjikën e route.

2. **Shto config env për AdMob App ID**
   - p.sh. `.env` + `process.env.ADMOB_APP_ID`
   - shmang hardcode për production.

3. **Shto test minimal për route critical**
   - `/`
   - `/category/:cat`
   - `/admin/stats`
   - `/api/mobile-ads-config`
