<?php
declare(strict_types=1);
require __DIR__ . '/_legal_common.php';
ss_legal_header(
    'Privacy Policy — Shif Shqip',
    'How Shif Shqip handles personal data in the Android app and web player /k1.',
    '/privacy.php'
);
$updated = '18 July 2026';
?>
<article class="legal">
  <h1>Privacy Policy</h1>
  <p class="lead">Last updated: <?= ss_h($updated) ?></p>

  <h2>1. Who we are</h2>
  <p><strong>Shif Shqip</strong> provides a <em>media player</em> (Android app and web at <code>/k1</code>) that plays sources you supply. We <strong>do not host TV channels</strong> and we are not an audiovisual content provider.</p>

  <h2>2. Data we may process</h2>
  <ul>
    <li><strong>Technical:</strong> IP address and basic device type needed to deliver the service.</li>
    <li><strong>Ads:</strong> AdMob / advertising partners may use Advertising ID, cookies, or SDKs under their own policies (see also in-app GDPR consent where shown).</li>
    <li><strong>Analytics:</strong> limited usage metrics for stability (crashes, performance) — we do not sell personal data.</li>
  </ul>

  <h2>3. Why we use data</h2>
  <ul>
    <li>To provide the player and related services.</li>
    <li>Security and service diagnostics.</li>
    <li>Advertising (when enabled) via the configured ad network.</li>
    <li>Legal compliance (for example, DMCA notices).</li>
  </ul>

  <h2>4. Storage &amp; transfers</h2>
  <p>Servers may be located in the EU or other regions. We use reasonable technical safeguards. We do not sell personal data.</p>

  <h2>5. Your rights (GDPR and similar)</h2>
  <p>If you are in the EU/UK you may request access, correction, deletion, restriction, objection, or portability — contact <a href="mailto:legal@shifshqip.com">legal@shifshqip.com</a>. We may ask for verification.</p>

  <h2>6. Children</h2>
  <p>The service is not directed at children under 16. If you believe a child has provided us data, contact us for deletion.</p>

  <h2>7. Changes</h2>
  <p>We may update this page. The “Last updated” date shows the current version.</p>

  <h2>8. Contact</h2>
  <p>Email: <a href="mailto:legal@shifshqip.com">legal@shifshqip.com</a></p>
</article>
<?php ss_legal_footer(); ?>
