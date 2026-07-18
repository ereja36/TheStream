<?php
/**
 * Shif Shqip — public homepage (static-first, CDN-cacheable).
 * Keeps AdSense / Monetag / Profiton / GA tags from the previous homepage.
 */
declare(strict_types=1);
require __DIR__ . '/_legal_common.php';

ss_legal_header(
    'Shif Shqip — IPTV Player & Web TV (no channel hosting)',
    'Shif Shqip is an IPTV media player app and web player. We do not host TV channels — users provide their own playlists. Privacy, Terms, DMCA, and AUP.',
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
            'inLanguage' => 'en',
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
  <div class="badge">MEDIA PLAYER · NOT A STREAMING HOST</div>
  <h1>Watch with your own playlist — we do not host TV channels</h1>
  <p class="lead">
    Shif Shqip is an <strong>IPTV media player</strong> (Android + ShifShqip portal).
    The app and website <strong>do not host, rebroadcast, or distribute TV channels</strong>.
    You (or your authorized provider) supply playlist / stream URLs; we provide playback software only.
  </p>
  <div style="margin-top:18px">
    <a class="btn btn-primary" href="/k1/">ShifShqip portal</a>
    <a class="btn btn-ghost" href="https://playlist.shifshqip.com/">Playlist portal</a>
  </div>
</section>

<div class="ad-slot">
  <ins class="adsbygoogle"
       style="display:block"
       data-ad-client="ca-pub-2519356856965635"
       data-ad-slot="auto"
       data-ad-format="auto"
       data-full-width-responsive="true"></ins>
  <script>(adsbygoogle = window.adsbygoogle || []).push({});</script>
</div>

<div class="grid">
  <div class="card">
    <h2>What the app is</h2>
    <p>An Android media player for M3U / Xtream-style portals. You add your own source. It is not a TV subscription service operated by Shif Shqip.</p>
  </div>
  <div class="card">
    <h2>What ShifShqip portal is</h2>
    <p>A browser-based player with the same model: it plays streams you provide. Legal pages (Privacy, Terms, DMCA, AUP) explain the boundaries of use.</p>
  </div>
  <div class="card">
    <h2>Compliance</h2>
    <p>Users must have legal rights to any source they play. We remove reported links under DMCA / notice-and-takedown. See <a href="/dmca.php">DMCA</a> and <a href="/acceptable-use.php">Acceptable Use</a>.</p>
  </div>
</div>

<div class="ad-slot">
  <ins class="adsbygoogle"
       style="display:block"
       data-ad-client="ca-pub-2519356856965635"
       data-ad-slot="auto"
       data-ad-format="auto"
       data-full-width-responsive="true"></ins>
  <script>(adsbygoogle = window.adsbygoogle || []).push({});</script>
</div>

<div class="card">
  <h2>Getting started</h2>
  <ul>
    <li>Open <a href="/k1/">ShifShqip portal</a> in your browser.</li>
    <li>Add an authorized playlist / portal (no pirate sources).</li>
    <li>Play channels inside the player.</li>
  </ul>
</div>
<?php ss_legal_footer(); ?>
