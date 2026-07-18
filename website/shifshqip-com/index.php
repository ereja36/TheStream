<?php
/**
 * Shif Shqip — public homepage (static-first, CDN-cacheable).
 * Replaces the previous ESPN sports scraper homepage.
 */
declare(strict_types=1);
require __DIR__ . '/_legal_common.php';

ss_legal_header(
    'Shif Shqip — IPTV Player & Web TV (pa hosting kanalesh)',
    'Shif Shqip është aplikacion dhe web player IPTV. Nuk hoston kanale TV: përdoruesi shton playlist-in e vet. Privacy, Terms, DMCA dhe AUP.',
    '/'
);

$jsonLd = [
    '@context' => 'https://schema.org',
    '@graph' => [
        [
            '@type' => 'Organization',
            'name' => 'Shif Shqip',
            'url' => 'https://shifshqip.com/',
            'logo' => 'https://shifshqip.com/logo.webp',
            'sameAs' => [
                'https://playlist.shifshqip.com/',
                'https://api.shifshqip.com/',
            ],
        ],
        [
            '@type' => 'WebSite',
            'name' => 'Shif Shqip',
            'url' => 'https://shifshqip.com/',
            'description' => 'IPTV media player app and browser player. Users bring their own playlist URLs; Shif Shqip does not host channel streams.',
            'inLanguage' => ['sq', 'en'],
            'potentialAction' => [
                '@type' => 'DownloadAction',
                'target' => 'https://api.shifshqip.com/shif-shqip-latest.apk',
            ],
        ],
        [
            '@type' => 'SoftwareApplication',
            'name' => 'Shif Shqip',
            'operatingSystem' => 'Android',
            'applicationCategory' => 'MultimediaApplication',
            'offers' => [
                '@type' => 'Offer',
                'price' => '0',
                'priceCurrency' => 'EUR',
            ],
        ],
    ],
];
echo '<script type="application/ld+json">' . json_encode($jsonLd, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE) . '</script>';
?>
<section class="hero">
  <div class="badge">MEDIA PLAYER · JO STREAMING HOST</div>
  <h1>Shiko TV me playlist-in tënd — pa hosting kanalesh nga ne</h1>
  <p class="lead">
    Shif Shqip është <strong>IPTV player</strong> (Android + web <code>/k1</code>).
    Aplikacioni dhe webi <strong>nuk hostojnë, nuk riprodhojnë dhe nuk shpërndajnë kanale TV</strong>.
    Ti (ose operatori yt i autorizuar) jep URL playlist / stream; ne ofrojmë vetëm softuerin e luajtjes.
  </p>
  <div style="margin-top:18px">
    <a class="btn btn-primary" href="https://api.shifshqip.com/shif-shqip-latest.apk">Shkarko APK</a>
    <a class="btn btn-ghost" href="/k1/">Hap Web TV /k1</a>
    <a class="btn btn-ghost" href="https://playlist.shifshqip.com/">Si funksionon playlist</a>
  </div>
</section>

<div class="grid">
  <div class="card">
    <h2>Çfarë është aplikacioni</h2>
    <p>Android media player për playlist M3U / portal Xtream-style. Përdoruesi shton burimin e vet. Nuk është shërbim TV me abonim nga Shif Shqip.</p>
  </div>
  <div class="card">
    <h2>Çfarë është /k1</h2>
    <p>Web player në shfletues për të njëjtin model: luajtje stream-esh që i jep përdoruesi. Ka faqe legale (Privacy, Terms, DMCA, AUP) që shpjegojnë kufijtë e përdorimit.</p>
  </div>
  <div class="card">
    <h2>Përputhshmëria</h2>
    <p>Përdoruesit duhet të kenë të drejtë ligjore për çdo burim që luajnë. Ne heqim lidhje të raportuara sipas DMCA / notice-and-takedown. Shiko <a href="/dmca.php">DMCA</a> dhe <a href="/acceptable-use.php">Acceptable Use</a>.</p>
  </div>
</div>

<div class="card">
  <h2>Si të fillosh</h2>
  <ul>
    <li>Instalo APK-në ose hap <a href="/k1/">/k1</a> në shfletues.</li>
    <li>Shto playlist / portal të autorizuar (jo linke pirate).</li>
    <li>Luaj kanalet brenda playerit — pa full-screen të detyruar në browse UI të app-it.</li>
  </ul>
</div>

<div class="card">
  <h2>Stabilitet &amp; DDoS</h2>
  <p>
    Faqja kryesore është e lehtë dhe cache-able nëpër CDN (Cloudflare).
    Stream-et dhe proxy-t janë të ndara nga marketing/legal pages që të mos bie site-i kur ka trafik të lartë.
    Rekomandojmë: Cloudflare “Under Attack” / WAF, rate-limit te origin, cache HTML statik.
  </p>
</div>
<?php ss_legal_footer(); ?>
