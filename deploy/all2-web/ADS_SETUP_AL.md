# Ads Setup (Admin + Premium Rules)

## Ndryshimet e kryera

- U hoqën seksionet:
  - Nimi Protection (opsionet 1/2/3/4)
  - Global EPG + Auto Logo Match
  - Kool Categories: Main Page + Proxy Override
- ALB API u hoq nga paneli i integrimeve dhe endpoint-et e saj kthejnë `410 Gone`.
- U shtua seksioni **Android AdMob (Wrapper)** në Admin Panel për vendosje direkte të IDs.

## Ku i menaxhon ads

Në `Admin -> Panel`:

1. **Banner Interstitial (Main + Category)**
   - Trigger në page load ose open channel
   - Frequency cap
   - Max views / 24h

2. **Ad Snippets (Banner + Interstitial HTML)**
   - Banner placement për Main dhe Category
     - `floating_bottom`
     - `floating_top`
     - `inline_top`
     - `inline_bottom`
     - `off`
   - Banner frequency cap
   - Banner max views / 24h
   - Premium rule:
     - `premium_ads_before_cooldown` (p.sh. 3)
     - `premium_cooldown_hours` (p.sh. 24)
     - `premium_after_cap_action`
       - `cooldown` (no ads për cooldown)
       - `continue_normal` (vazhdo normal)

## Si ta testosh snippet-in tënd

Vendose te **Banner HTML/JS** ose **Interstitial HTML/JS**:

```html
<script async="async" data-cfasync="false" src="https://pl28854081.effectivegatecpm.com/632f68da3e2b043a44a5b4e69ee4e6c4/invoke.js"></script>
<div id="container-632f68da3e2b043a44a5b4e69ee4e6c4"></div>
```

Engine-i i faqes i ri-ekzekuton `<script>` tags nga snippet-i.

## Premium tier në frontend

Në console ose script:

```js
window.setAdUserTier("premium"); // ose "normal"
```

Gjithashtu mund ta kalosh me query param:

```text
?tier=premium
```

(emri i parametrit është i konfiguruar nga admini te `premium query param`).

## Sugjerime vizuale për main page

1. **Floating top banner** vetëm në main.
2. **Inline bottom banner** në kategori për më pak ndërprerje.
3. **Interstitial vetëm në open channel** (jo në page load) për UX më të mirë.
4. Test A/B:
   - Variant A: floating_bottom + frequency 60s
   - Variant B: inline_top + frequency 90s
