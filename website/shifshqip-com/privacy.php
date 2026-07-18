<?php
declare(strict_types=1);
require __DIR__ . '/_legal_common.php';
ss_legal_header(
    'Privacy Policy — Shif Shqip',
    'Si i trajton Shif Shqip të dhënat personale në aplikacionin Android dhe web player /k1.',
    '/privacy.php'
);
$updated = '18 July 2026';
?>
<article class="legal">
  <h1>Privacy Policy</h1>
  <p class="lead">Last updated: <?= ss_h($updated) ?></p>

  <h2>1. Kush jemi</h2>
  <p><strong>Shif Shqip</strong> ofron një <em>media player</em> (aplikacion Android dhe web në <code>/k1</code>) që luan burime që i jep përdoruesi. Ne <strong>nuk hostojmë kanale TV</strong> dhe nuk jemi ofrues përmbajtjeje audiovizive.</p>

  <h2>2. Çfarë të dhënash mbledhim</h2>
  <ul>
    <li><strong>Teknike:</strong> IP, lloji i pajisjes, versioni i app/OS, identifikues lokal pajisjeje (për licencë / anti-abuse), loge serveri.</li>
    <li><strong>Playlist / URL:</strong> nëse i ruan në pajisje ose i dërgon te serverët tanë për portal të mbrojtur, trajtohen si të dhëna shërbimi.</li>
    <li><strong>Ads:</strong> AdMob / partnerë reklamash mund të përdorin Advertising ID, cookies / SDK sipas politikave të tyre (shiko edhe GDPR consent në app).</li>
    <li><strong>Analitikë:</strong> matje të kufizuara përdorimi për stabilitet (crash, performancë), jo shitje të dhënash.</li>
  </ul>

  <h2>3. Pse i përdorim</h2>
  <ul>
    <li>Të ofrojmë playerin dhe portalin e playlist-it.</li>
    <li>Siguri, anti-abuse, diagnostikë.</li>
    <li>Reklama (kur janë aktive) sipas zgjedhjes së rrjetit ads.</li>
    <li>Përmbushje ligjore (p.sh. DMCA notices).</li>
  </ul>

  <h2>4. Ruajtja &amp; transferet</h2>
  <p>Serverët mund të jenë në BE / vende të tjera. Përdorim masa teknike të arsyeshme. Nuk shesim të dhëna personale.</p>

  <h2>5. Të drejtat e tua (GDPR / e ngjashme)</h2>
  <p>Nëse je në BE/UK: akses, korrigjim, fshirje, kufizim, kundërshtim, portueshmëri — kontakto <a href="mailto:legal@shifshqip.com">legal@shifshqip.com</a>. Mund të të kërkojmë verifikim.</p>

  <h2>6. Fëmijët</h2>
  <p>Shërbimi nuk synon fëmijë nën 16 vjeç. Nëse mëson se një fëmijë na ka dhënë të dhëna, na shkruaj për fshirje.</p>

  <h2>7. Ndryshime</h2>
  <p>Mund ta përditësojmë këtë faqe. Data “Last updated” tregon versionin aktiv.</p>

  <h2>8. Kontakt</h2>
  <p>Email: <a href="mailto:legal@shifshqip.com">legal@shifshqip.com</a></p>
</article>
<?php ss_legal_footer(); ?>
