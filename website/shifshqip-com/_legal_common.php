<?php
/** Shared chrome for Shif Shqip legal / marketing pages. */
declare(strict_types=1);

function ss_h(string $s): string {
    return htmlspecialchars($s, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function ss_legal_header(string $title, string $description, string $canonicalPath = '/', array $extra = []): void {
    $host = 'https://shifshqip.com';
    $canonical = $host . $canonicalPath;
    $titleEsc = ss_h($title);
    $descEsc = ss_h($description);
    $canonEsc = ss_h($canonical);
    $lang = strtolower((string)($extra['lang'] ?? 'sq'));
    if ($lang !== 'en') {
        $lang = 'sq';
    }
    $htmlLang = $lang === 'en' ? 'en' : 'sq';
    $metaLang = $lang === 'en' ? 'English' : 'Albanian';
    $ogLocale = $lang === 'en' ? 'en_US' : 'sq_AL';
    $keywords = ss_h($extra['keywords'] ?? (
        $lang === 'en'
            ? 'Shif Shqip, IPTV player, Android player, M3U playlist, privacy policy, media player'
            : 'Shif Shqip, IPTV player, portal TV, player Android, M3U, playlist, web TV Shqipëri, media player'
    ));
    header('Content-Type: text/html; charset=utf-8');
    header('X-Content-Type-Options: nosniff');
    header('Referrer-Policy: strict-origin-when-cross-origin');
    header('Cache-Control: public, max-age=600, s-maxage=3600, stale-while-revalidate=86400');
    echo '<!DOCTYPE html><html lang="' . $htmlLang . '"><head>';
    echo '<meta charset="utf-8">';
    echo '<meta name="viewport" content="width=device-width, initial-scale=1">';
    echo '<title>' . $titleEsc . '</title>';
    echo '<meta name="description" content="' . $descEsc . '">';
    echo '<meta name="keywords" content="' . $keywords . '">';
    echo '<meta name="robots" content="index,follow,max-image-preview:large,max-snippet:-1">';
    echo '<meta name="author" content="Shif Shqip">';
    echo '<meta name="language" content="' . ss_h($metaLang) . '">';
    echo '<link rel="canonical" href="' . $canonEsc . '">';
    echo '<link rel="alternate" hreflang="en" href="' . $canonEsc . '">';
    echo '<link rel="alternate" hreflang="sq" href="' . $canonEsc . '">';
    echo '<link rel="alternate" hreflang="x-default" href="' . $canonEsc . '">';
    echo '<meta property="og:locale" content="' . $ogLocale . '">';
    echo '<meta property="og:type" content="website">';
    echo '<meta property="og:site_name" content="Shif Shqip">';
    echo '<meta property="og:title" content="' . $titleEsc . '">';
    echo '<meta property="og:description" content="' . $descEsc . '">';
    echo '<meta property="og:url" content="' . $canonEsc . '">';
    echo '<meta property="og:image" content="https://shifshqip.com/logo.webp">';
    echo '<meta name="twitter:card" content="summary_large_image">';
    echo '<meta name="twitter:title" content="' . $titleEsc . '">';
    echo '<meta name="twitter:description" content="' . $descEsc . '">';
    echo '<meta name="theme-color" content="#0b1220">';
    echo '<meta name="profiton-domain-verification" content="3ac22be6709443fe7b40175013d9ac4cdd84ab7f2a652280eab8d690f7840a83">';
    echo '<meta name="monetag" content="9d4c3909ebe45292254d838ef1f5f1dd">';
    echo '<meta name="google-adsense-account" content="ca-pub-2519356856965635">';
    echo '<script async src="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=ca-pub-2519356856965635" crossorigin="anonymous"></script>';
    echo '<script async src="https://www.googletagmanager.com/gtag/js?id=G-NZW2V12T95"></script>';
    echo '<script>window.dataLayer=window.dataLayer||[];function gtag(){dataLayer.push(arguments);}gtag("js",new Date());gtag("config","G-NZW2V12T95");</script>';
    echo '<link rel="preconnect" href="https://fonts.googleapis.com">';
    echo '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>';
    echo '<link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;700&family=Syne:wght@700;800&display=swap" rel="stylesheet">';
    echo '<style>
:root{--bg:#070b14;--bg2:#0d1424;--card:#121a2c;--text:#f3f6fb;--muted:#9aa8c0;--line:rgba(255,255,255,.1);--accent:#e11d48;--ok:#3dd68c}
*{box-sizing:border-box}body{margin:0;font-family:"DM Sans",system-ui,sans-serif;background:radial-gradient(900px 420px at 12% -10%,rgba(225,29,72,.18),transparent 55%),radial-gradient(700px 380px at 90% 0%,rgba(61,214,140,.12),transparent 50%),linear-gradient(180deg,var(--bg),var(--bg2));color:var(--text);line-height:1.65;min-height:100vh}
a{color:#7dd3fc;text-decoration:none}a:hover{text-decoration:underline}
.wrap{max-width:920px;margin:0 auto;padding:28px 18px 64px}
nav{display:flex;flex-wrap:wrap;gap:10px 16px;align-items:center;justify-content:space-between;margin-bottom:28px;padding-bottom:16px;border-bottom:1px solid var(--line)}
.brand{font-family:Syne,sans-serif;font-weight:800;font-size:1.25rem;color:#fff;letter-spacing:-.02em}
.nav-links{display:flex;flex-wrap:wrap;gap:12px;font-size:.92rem}
.nav-links a{color:var(--muted)}
.hero{padding:28px 0 8px}h1{font-family:Syne,sans-serif;font-size:clamp(1.8rem,4vw,2.6rem);line-height:1.15;margin:0 0 12px;letter-spacing:-.03em}
.lead{color:var(--muted);font-size:1.05rem;max-width:62ch;margin:0}
.card{background:linear-gradient(180deg,rgba(255,255,255,.04),rgba(255,255,255,.015));border:1px solid var(--line);border-radius:18px;padding:22px 20px;margin:18px 0}
.card h2{font-family:Syne,sans-serif;font-size:1.2rem;margin:0 0 10px}
.card p,.card li{color:var(--muted)}
.card ul{padding-left:1.2rem;margin:8px 0}
.grid{display:grid;gap:14px;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));margin-top:18px}
.btn{display:inline-block;margin:6px 8px 6px 0;padding:12px 18px;border-radius:999px;font-weight:700;text-decoration:none!important}
.btn-primary{background:var(--accent);color:#fff}.btn-ghost{border:1px solid var(--line);color:#fff}
.ad-slot{margin:18px 0;min-height:90px;display:flex;justify-content:center;align-items:center}
.badge{display:inline-block;padding:4px 10px;border-radius:999px;background:rgba(61,214,140,.12);color:var(--ok);font-size:.78rem;font-weight:700;margin-bottom:10px}
footer{margin-top:40px;padding-top:18px;border-top:1px solid var(--line);color:var(--muted);font-size:.88rem}
footer a{color:var(--muted);margin-right:12px}
.legal h2{margin-top:1.6em}.legal p,.legal li{color:var(--muted)}
.faq details{border:1px solid var(--line);border-radius:14px;padding:14px 16px;margin:10px 0;background:rgba(255,255,255,.02)}
.faq summary{cursor:pointer;font-weight:700;color:#fff}
@media (max-width:640px){.nav-links{width:100%}}
</style>';
    echo '</head><body><div class="wrap"><nav>';
    echo '<a class="brand" href="/">Shif Shqip</a><div class="nav-links">';
    if ($lang === 'en') {
        echo '<a href="/">Home</a><a href="/k1/">ShifShqip portal</a><a href="/privacy.php">Privacy</a><a href="/terms.php">Terms</a><a href="/dmca.php">DMCA</a><a href="/acceptable-use.php">Acceptable Use</a>';
    } else {
        echo '<a href="/">Kryefaqja</a><a href="/k1/">ShifShqip portal</a><a href="/privacy.php">Privatësia</a><a href="/terms.php">Kushtet</a><a href="/dmca.php">DMCA</a><a href="/acceptable-use.php">Përdorimi</a>';
    }
    echo '</div></nav>';
    $GLOBALS['ss_legal_lang'] = $lang;
}

function ss_legal_footer(): void {
    $lang = $GLOBALS['ss_legal_lang'] ?? 'sq';
    echo '<footer>';
    if ($lang === 'en') {
        echo '<a href="/privacy.php">Privacy Policy</a>';
        echo '<a href="/terms.php">Terms of Use</a>';
        echo '<a href="/dmca.php">DMCA</a>';
        echo '<a href="/acceptable-use.php">Acceptable Use</a>';
        echo '<a href="https://playlist.shifshqip.com/">Playlist Portal</a>';
        echo '<p style="margin-top:14px">© ' . date('Y') . ' Shif Shqip. Media player / playlist client — we do not host or broadcast TV channels.</p>';
    } else {
        echo '<a href="/privacy.php">Politika e Privatësisë</a>';
        echo '<a href="/terms.php">Kushtet e Përdorimit</a>';
        echo '<a href="/dmca.php">DMCA</a>';
        echo '<a href="/acceptable-use.php">Përdorimi i Lejuar</a>';
        echo '<a href="https://playlist.shifshqip.com/">Portal Playlist</a>';
        echo '<p style="margin-top:14px">© ' . date('Y') . ' Shif Shqip. Media player / klient playlist — nuk hostojmë dhe nuk transmetojmë kanale TV.</p>';
    }
    echo '</footer></div></body></html>';
}
