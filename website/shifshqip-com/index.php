<?php
/**
 * Shif Shqip — kryefaqja publike (shqip, SEO).
 * Ruan tag-et AdSense / Monetag / Profiton / GA.
 */
declare(strict_types=1);
require __DIR__ . '/_legal_common.php';

ss_legal_header(
    'Shif Shqip — IPTV Player & Portal TV (pa hosting kanalesh)',
    'Shif Shqip është aplikacion dhe portal media player IPTV. Nuk hoston kanale TV: ti shton playlist-in tënd. ShifShqip portal, privatësi, kushte, DMCA.',
    '/',
    [
        'keywords' => 'Shif Shqip, IPTV player Shqipëri, portal TV, media player Android, playlist M3U, ShifShqip portal, web TV, player kanalesh, pa hosting kanalesh',
    ]
);

$jsonLd = [
    '@context' => 'https://schema.org',
    '@graph' => [
        [
            '@type' => 'Organization',
            'name' => 'Shif Shqip',
            'url' => 'https://shifshqip.com/',
            'logo' => 'https://shifshqip.com/logo.webp',
            'description' => 'Media player IPTV dhe portal web. Nuk hoston kanale TV.',
            'sameAs' => [
                'https://playlist.shifshqip.com/',
                'https://api.shifshqip.com/',
            ],
        ],
        [
            '@type' => 'WebSite',
            'name' => 'Shif Shqip',
            'url' => 'https://shifshqip.com/',
            'description' => 'Aplikacion dhe portal media player IPTV. Përdoruesi sjell playlist-in e vet; Shif Shqip nuk hoston stream-e kanalesh.',
            'inLanguage' => 'sq',
            'publisher' => ['@type' => 'Organization', 'name' => 'Shif Shqip'],
        ],
        [
            '@type' => 'SoftwareApplication',
            'name' => 'Shif Shqip',
            'operatingSystem' => 'Android',
            'applicationCategory' => 'MultimediaApplication',
            'description' => 'Media player për playlist M3U / portal. Softuer luajtjeje, jo shërbim TV me kanale të hostuara.',
            'offers' => [
                '@type' => 'Offer',
                'price' => '0',
                'priceCurrency' => 'EUR',
            ],
            'inLanguage' => 'sq',
        ],
        [
            '@type' => 'FAQPage',
            'mainEntity' => [
                [
                    '@type' => 'Question',
                    'name' => 'A hoston Shif Shqip kanale TV?',
                    'acceptedAnswer' => [
                        '@type' => 'Answer',
                        'text' => 'Jo. Shif Shqip është softuer media player. Ti (ose operatori yt i autorizuar) jep URL playlist / stream; ne ofrojmë vetëm softuerin e luajtjes.',
                    ],
                ],
                [
                    '@type' => 'Question',
                    'name' => 'Çfarë është ShifShqip portal?',
                    'acceptedAnswer' => [
                        '@type' => 'Answer',
                        'text' => 'ShifShqip portal është playeri web në shfletues (/k1) me të njëjtin model: luan burime që i jep përdoruesi, pa hosting kanalesh nga ne.',
                    ],
                ],
                [
                    '@type' => 'Question',
                    'name' => 'A është legale përdorimi i Shif Shqip?',
                    'acceptedAnswer' => [
                        '@type' => 'Answer',
                        'text' => 'Softueri është player. Përdoruesi duhet të ketë të drejtë ligjore për çdo burim që luan. Shiko Kushtet, AUP dhe DMCA.',
                    ],
                ],
            ],
        ],
    ],
];
echo '<script type="application/ld+json">' . json_encode($jsonLd, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE) . '</script>';
?>
<section class="hero">
  <div class="badge">MEDIA PLAYER · JO HOSTING KANALESH</div>
  <h1>Shiko TV me playlist-in tënd — pa hosting kanalesh nga ne</h1>
  <p class="lead">
    <strong>Shif Shqip</strong> është <strong>IPTV media player</strong> (Android + <strong>ShifShqip portal</strong>).
    Aplikacioni dhe webi <strong>nuk hostojnë, nuk riprodhojnë dhe nuk shpërndajnë kanale TV</strong>.
    Ti (ose operatori yt i autorizuar) jep URL playlist / stream; ne ofrojmë vetëm softuerin e luajtjes.
  </p>
  <div style="margin-top:18px">
    <a class="btn btn-primary" href="/k1/">Hap ShifShqip portal</a>
    <a class="btn btn-ghost" href="https://playlist.shifshqip.com/">Portal Playlist</a>
    <a class="btn btn-ghost" href="/terms.php">Kushtet</a>
  </div>
</section>

<div class="ad-slot">
  <ins class="adsbygoogle"
       style="display:block"
       data-ad-client="ca-pub-2519356856965635"
       data-ad-slot="AUTO"
       data-ad-format="auto"
       data-full-width-responsive="true"></ins>
  <script>(adsbygoogle = window.adsbygoogle || []).push({});</script>
</div>

<div class="grid">
  <div class="card">
    <h2>Çfarë është aplikacioni</h2>
    <p>Media player Android për playlist M3U / portal Xtream-style. Ti shton burimin tënd. Nuk është shërbim TV me abonim nga Shif Shqip.</p>
  </div>
  <div class="card">
    <h2>Çfarë është ShifShqip portal</h2>
    <p>Player web në shfletues me të njëjtin model: luan stream-e që i jep përdoruesi. Faqet legale (Privatësia, Kushtet, DMCA, AUP) shpjegojnë kufijtë e përdorimit.</p>
  </div>
  <div class="card">
    <h2>Përputhshmëria</h2>
    <p>Përdoruesit duhet të kenë të drejtë ligjore për çdo burim që luajnë. Heqim lidhje të raportuara sipas DMCA. Shiko <a href="/dmca.php">DMCA</a> dhe <a href="/acceptable-use.php">Përdorimin e Lejuar</a>.</p>
  </div>
</div>

<div class="ad-slot">
  <ins class="adsbygoogle"
       style="display:block"
       data-ad-client="ca-pub-2519356856965635"
       data-ad-slot="AUTO"
       data-ad-format="auto"
       data-full-width-responsive="true"></ins>
  <script>(adsbygoogle = window.adsbygoogle || []).push({});</script>
</div>

<div class="card">
  <h2>Si të fillosh</h2>
  <ul>
    <li>Hap <a href="/k1/">ShifShqip portal</a> në shfletues.</li>
    <li>Shto playlist / portal të autorizuar (jo burime pirate).</li>
    <li>Luaj kanalet brenda playerit.</li>
  </ul>
</div>

<section class="card faq" aria-label="Pyetje të shpeshta">
  <h2>Pyetje të shpeshta</h2>
  <details>
    <summary>A hoston Shif Shqip kanale TV?</summary>
    <p>Jo. Jemi softuer media player. Ti sjell burimin; ne ofrojmë luajtjen.</p>
  </details>
  <details>
    <summary>Çfarë është ShifShqip portal?</summary>
    <p>Playeri web në <a href="/k1/">/k1</a> — i njëjti model si app-i, në shfletues.</p>
  </details>
  <details>
    <summary>Ku i gjej kushtet ligjore?</summary>
    <p><a href="/terms.php">Kushtet</a>, <a href="/privacy.php">Privatësia</a>, <a href="/dmca.php">DMCA</a>, <a href="/acceptable-use.php">Përdorimi i Lejuar</a>.</p>
  </details>
</section>
<?php ss_legal_footer(); ?>
