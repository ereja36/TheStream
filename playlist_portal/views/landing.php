<?php
/** @var string $apkUrlEsc */
/** @var string $playStoreEsc */
/** @var string $publicUrlEsc */
?>
<!DOCTYPE html>
<html lang="sq">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="theme-color" content="#0c1017">
    <title>Shif Shqip IPTV — Playlist</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,400;0,9..40,500;0,9..40,600;0,9..40,700;1,9..40,400&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg: #0c1017;
            --bg-soft: #121822;
            --card: rgba(22, 28, 38, 0.92);
            --line: rgba(255, 255, 255, 0.08);
            --text: #f4f6fa;
            --muted: #9aa6b8;
            --accent: #8db04b;
            --accent-soft: rgba(141, 176, 75, 0.16);
            --accent-glow: rgba(141, 176, 75, 0.35);
            --radius: 18px;
        }

        * { box-sizing: border-box; }

        body {
            margin: 0;
            min-height: 100vh;
            font-family: "DM Sans", system-ui, -apple-system, sans-serif;
            color: var(--text);
            background:
                radial-gradient(ellipse 80% 50% at 50% -10%, rgba(141, 176, 75, 0.18), transparent 55%),
                radial-gradient(ellipse 60% 40% at 100% 0%, rgba(56, 120, 200, 0.12), transparent 50%),
                var(--bg);
            line-height: 1.6;
        }

        .wrap {
            max-width: 720px;
            margin: 0 auto;
            padding: 32px 20px 48px;
        }

        .hero {
            text-align: center;
            margin-bottom: 28px;
        }

        .badge {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 6px 14px;
            border-radius: 999px;
            background: var(--accent-soft);
            border: 1px solid rgba(141, 176, 75, 0.28);
            color: #c8e08a;
            font-size: 13px;
            font-weight: 600;
            letter-spacing: 0.02em;
            margin-bottom: 18px;
        }

        .badge-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: var(--accent);
            box-shadow: 0 0 12px var(--accent-glow);
        }

        h1 {
            margin: 0 0 10px;
            font-size: clamp(2rem, 6vw, 2.6rem);
            font-weight: 700;
            letter-spacing: -0.03em;
        }

        .lead {
            margin: 0 auto;
            max-width: 520px;
            color: var(--muted);
            font-size: 1.05rem;
        }

        .card {
            background: var(--card);
            border: 1px solid var(--line);
            border-radius: calc(var(--radius) + 4px);
            padding: 24px;
            margin-bottom: 18px;
            backdrop-filter: blur(12px);
            box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
        }

        .card h2 {
            margin: 0 0 6px;
            font-size: 1.15rem;
            font-weight: 700;
        }

        .card p.section-note {
            margin: 0 0 18px;
            color: var(--muted);
            font-size: 0.95rem;
        }

        .steps {
            list-style: none;
            margin: 0;
            padding: 0;
            display: grid;
            gap: 14px;
        }

        .step {
            display: grid;
            grid-template-columns: 40px 1fr;
            gap: 14px;
            align-items: start;
            padding: 14px 16px;
            border-radius: 14px;
            background: var(--bg-soft);
            border: 1px solid var(--line);
        }

        .step-num {
            width: 40px;
            height: 40px;
            border-radius: 12px;
            display: grid;
            place-items: center;
            font-weight: 700;
            font-size: 0.95rem;
            color: var(--accent);
            background: var(--accent-soft);
            border: 1px solid rgba(141, 176, 75, 0.25);
        }

        .step strong {
            display: block;
            margin-bottom: 4px;
            font-size: 0.98rem;
        }

        .step span {
            color: var(--muted);
            font-size: 0.92rem;
        }

        .url-box {
            display: flex;
            gap: 10px;
            margin-top: 16px;
            flex-wrap: wrap;
        }

        .url-field {
            flex: 1 1 220px;
            min-width: 0;
            padding: 14px 16px;
            border-radius: 12px;
            border: 1px solid var(--line);
            background: #0a0e14;
            color: #d7e3ff;
            font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
            font-size: 0.88rem;
            word-break: break-all;
        }

        .btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            padding: 14px 20px;
            border-radius: 12px;
            border: none;
            font: inherit;
            font-weight: 700;
            font-size: 0.95rem;
            text-decoration: none;
            cursor: pointer;
            transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
        }

        .btn:active { transform: scale(0.98); }

        .btn-copy {
            background: var(--accent);
            color: #10140c;
            box-shadow: 0 8px 24px var(--accent-glow);
            white-space: nowrap;
        }

        .btn-copy.copied {
            background: #6f9438;
            color: #fff;
        }

        .downloads {
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
            justify-content: center;
            margin-top: 8px;
        }

        .btn-primary {
            background: var(--accent);
            color: #10140c;
            box-shadow: 0 8px 24px var(--accent-glow);
        }

        .btn-ghost {
            background: transparent;
            color: var(--text);
            border: 1px solid var(--line);
        }

        .tip {
            margin-top: 14px;
            padding: 12px 14px;
            border-radius: 12px;
            background: rgba(255, 255, 255, 0.03);
            border: 1px dashed rgba(255, 255, 255, 0.1);
            color: var(--muted);
            font-size: 0.9rem;
        }

        .footer {
            text-align: center;
            margin-top: 22px;
            color: #6f7b8d;
            font-size: 0.85rem;
        }

        @media (max-width: 520px) {
            .wrap { padding-top: 22px; }
            .card { padding: 18px; }
            .url-box { flex-direction: column; }
            .btn-copy { width: 100%; }
        }
    </style>
</head>
<body>
    <div class="wrap">
        <header class="hero">
            <div class="badge"><span class="badge-dot"></span> Shif Shqip IPTV</div>
            <h1>Playlist për aplikacionin</h1>
            <p class="lead">Shiko kanalet shqiptare në telefon ose Android TV. Mjafton të shtosh linkun e playlistës në app.</p>
        </header>

        <section class="card">
            <h2>Si ta shtosh në app</h2>
            <p class="section-note">Ndiq këto hapa pas instalimit të Shif Shqip.</p>
            <ol class="steps">
                <li class="step">
                    <div class="step-num">1</div>
                    <div>
                        <strong>Instalo aplikacionin</strong>
                        <span>Shkarko APK ose instalo nga Google Play në telefon / TV Android.</span>
                    </div>
                </li>
                <li class="step">
                    <div class="step-num">2</div>
                    <div>
                        <strong>Hap Shif Shqip</strong>
                        <span>Në hapjen e parë shfaqet ekrani për shtimin e playlistës.</span>
                    </div>
                </li>
                <li class="step">
                    <div class="step-num">3</div>
                    <div>
                        <strong>Prek «Add Playlist»</strong>
                        <span>Zgjidh opsionin për të futur një link M3U (URL playliste).</span>
                    </div>
                </li>
                <li class="step">
                    <div class="step-num">4</div>
                    <div>
                        <strong>Ngjit URL-në e playlistës</strong>
                        <span>Kopjo linkun më poshtë dhe ruaje — kanalet ngarkohen automatikisht.</span>
                    </div>
                </li>
            </ol>

            <div class="url-box">
                <div class="url-field" id="playlist-url"><?php echo $publicUrlEsc; ?></div>
                <button type="button" class="btn btn-copy" id="copy-btn">Kopjo linkun</button>
            </div>

            <p class="tip">Nëse e ke mbyllur ekranin e parë: Cilësimet → Manage Playlists → Add Playlist → ngjit të njëjtin URL.</p>
        </section>

        <section class="card">
            <h2>Shkarko aplikacionin</h2>
            <p class="section-note">Versioni më i fundit për Android.</p>
            <div class="downloads">
                <a class="btn btn-primary" href="<?php echo $apkUrlEsc; ?>">Shkarko APK</a>
                <a class="btn btn-ghost" href="<?php echo $playStoreEsc; ?>">Google Play</a>
            </div>
        </section>

        <p class="footer">playlist.shifshqip.com · Shif Shqip</p>
    </div>

    <script>
        (function () {
            var url = <?php echo json_encode($publicUrlEsc, JSON_UNESCAPED_SLASHES); ?>;
            var btn = document.getElementById('copy-btn');
            if (!btn) return;

            function setCopied() {
                btn.textContent = 'U kopjua!';
                btn.classList.add('copied');
                window.setTimeout(function () {
                    btn.textContent = 'Kopjo linkun';
                    btn.classList.remove('copied');
                }, 2000);
            }

            btn.addEventListener('click', function () {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(url).then(setCopied).catch(fallback);
                    return;
                }
                fallback();
            });

            function fallback() {
                var field = document.getElementById('playlist-url');
                if (!field) return;
                var range = document.createRange();
                range.selectNodeContents(field);
                var sel = window.getSelection();
                sel.removeAllRanges();
                sel.addRange(range);
                try {
                    document.execCommand('copy');
                    setCopied();
                } catch (e) {
                    btn.textContent = 'Kopjo manualisht';
                }
                sel.removeAllRanges();
            }
        })();
    </script>
</body>
</html>
