<?php

$host = 'localhost';
$user = 'shif_user';
$pass = 'FJALEKALIMI_YT';
$database = 'shif_shqip_db';

$portal_public_host = 'playlist.shifshqip.com';
$apk_download_url = 'https://api.shifshqip.com/shif-shqip-latest.apk';
$play_store_url = 'https://play.google.com/store/apps/details?id=com.shif.shqip.android';

$connect = new mysqli($host, $user, $pass, $database);
if ($connect->connect_errno) {
    http_response_code(500);
    header('Content-Type: text/plain; charset=utf-8');
    echo 'Database connection failed';
    exit;
}
$connect->set_charset('utf8mb4');
