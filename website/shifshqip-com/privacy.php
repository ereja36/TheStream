<?php
declare(strict_types=1);
require __DIR__ . '/_legal_common.php';
ss_legal_header(
    'Privacy Policy — Shif Shqip',
    'How Shif Shqip handles personal data in the Android app and ShifShqip portal.',
    '/privacy.php',
    [
        'lang' => 'en',
        'keywords' => 'Shif Shqip, privacy policy, Android app, media player, AdMob, GDPR, Play Store',
    ]
);
$updated = '19 July 2026';
?>
<article class="legal">
  <h1>Privacy Policy</h1>
  <p class="lead">Last updated: <?= ss_h($updated) ?></p>

  <h2>1. Who we are</h2>
  <p><strong>Shif Shqip</strong> provides a <em>media player</em> (Android app and ShifShqip portal) that plays sources supplied by the user. We <strong>do not host TV channels</strong> and we are not an audiovisual content provider.</p>

  <h2>2. Data we may process</h2>
  <ul>
    <li><strong>Technical:</strong> IP address and basic device/app information needed to provide the service and keep it secure.</li>
    <li><strong>Advertising:</strong> Google AdMob and other ad partners may process Advertising ID, cookies, or SDK identifiers according to their own policies.</li>
    <li><strong>Analytics:</strong> limited stability metrics (for example crashes and performance). We do not sell personal data.</li>
    <li><strong>Account (if used):</strong> email or login identifiers you choose to provide for optional features such as favorites or premium access.</li>
  </ul>

  <h2>3. Why we use data</h2>
  <ul>
    <li>To operate the player and related services.</li>
    <li>Security, abuse prevention, and diagnostics.</li>
    <li>Advertising (when ads are enabled).</li>
    <li>Legal compliance (for example DMCA or similar notices).</li>
  </ul>

  <h2>4. Advertising &amp; third parties</h2>
  <p>The Android app may show ads via <strong>Google AdMob</strong> and related Google services. Those partners may collect device identifiers and usage data to deliver and measure ads. See Google’s policies for details. Web pages may also use AdSense or similar networks.</p>

  <h2>5. Storage &amp; transfers</h2>
  <p>Servers may be located in the EU or other regions. We use reasonable technical measures. We do not sell personal data.</p>

  <h2>6. Your rights (GDPR and similar)</h2>
  <p>If you are in the EU/UK (or another region with similar laws), you may request access, correction, deletion, restriction, objection, or portability by contacting <a href="mailto:legal@shifshqip.com">legal@shifshqip.com</a>.</p>

  <h2>7. Children</h2>
  <p>The service is not directed to children under 16. If you believe a child has provided us data, contact us to request deletion.</p>

  <h2>8. Changes</h2>
  <p>We may update this page. The “Last updated” date shows the active version.</p>

  <h2>9. Contact</h2>
  <p>Email: <a href="mailto:legal@shifshqip.com">legal@shifshqip.com</a><br>
  Website: <a href="https://shifshqip.com/">https://shifshqip.com/</a></p>
</article>
<?php ss_legal_footer(); ?>
