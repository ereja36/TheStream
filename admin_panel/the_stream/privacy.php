<!DOCTYPE html>
<html lang="en">

<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1.0, user-scalable=no">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="description" content="Shif Shqip Privacy Policy">
    <title>Privacy Policy — Shif Shqip</title>
    <link rel="icon" href="assets/images/favicon.png" type="image/x-icon">
    <link href="https://fonts.googleapis.com/css?family=Roboto:400,700&subset=latin,cyrillic-ext" rel="stylesheet" type="text/css">
    <link href="assets/plugins/bootstrap/css/bootstrap.css" rel="stylesheet">
    <style>
        body { font-family: Roboto, sans-serif; line-height: 1.7; color: #333; background: #f5f5f5; }
        .policy-wrap { max-width: 860px; margin: 32px auto; background: #fff; padding: 32px 36px; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,.08); }
        h1 { font-size: 28px; margin-bottom: 8px; color: #E31E24; }
        h3 { margin-top: 28px; margin-bottom: 12px; font-size: 18px; color: #222; }
        p, li { font-size: 15px; }
        ul { padding-left: 22px; }
        .meta { color: #777; font-size: 13px; margin-bottom: 24px; }
        a { color: #E31E24; }
    </style>
</head>

<body>
    <div class="policy-wrap">
        <h1>Privacy Policy</h1>
        <p class="meta"><strong>Shif Shqip</strong> · Effective date: 2026-06-25 · Contact: <a href="mailto:shifshqip@gmail.com">shifshqip@gmail.com</a></p>

        <?php
        header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
        header('Pragma: no-cache');
        include('includes/config.php');
        $sql = "SELECT privacy_policy FROM tbl_settings ORDER BY id DESC LIMIT 1";
        $result = $connect->query($sql);
        $data = $result->fetch_assoc();
        echo $data['privacy_policy'];
        ?>
    </div>
</body>

</html>
